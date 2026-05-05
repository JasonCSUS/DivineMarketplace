package divinejason.divinemarketplace.auction.service;

import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.MarketProfile;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.config.ConfigService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure-Java market recommendation logic.
 *
 * Current role:
 * - produces an in-memory MarketProfile object during recalculation
 * - does not assume persisted legacy market profile binary cache storage exists
 * - lets recommendation math stay readable even though final recommended prices
 *   are persisted elsewhere
 */
public final class MarketProfileCalculator {

    public MarketProfile calculate(
            String marketKey,
            MarketProfile oldProfile,
            List<SaleRecord> recentSales,
            List<Listing> activeListings,
            long nowEpochMillis
    ) {
        long averageSaleUnitPrice = computeAverageSaleUnitPrice(recentSales);
        long averageListingUnitPrice = computeAverageListingUnitPrice(activeListings);

        TrendData trend = computeRecentSaleTrend(recentSales);

        int recentSaleSampleCount = recentSales == null ? 0 : recentSales.size();
        int recentListingSampleCount = activeListings == null ? 0 : activeListings.size();

        long currentRecommendation = oldProfile.currentRecommendedUnitPrice();
        if (currentRecommendation <= 0L) {
            currentRecommendation = bootstrapRecommendation(
                    averageSaleUnitPrice,
                    averageListingUnitPrice
            );
        }

        long newRecommendation = calculateRecommendedUnitPrice(
                currentRecommendation,
                averageSaleUnitPrice,
                averageListingUnitPrice,
                trend.slope(),
                trend.fitness(),
                recentSaleSampleCount,
                recentListingSampleCount
        );

        return new MarketProfile(
                marketKey,
                newRecommendation,
                averageSaleUnitPrice,
                averageListingUnitPrice,
                trend.slope(),
                trend.fitness(),
                recentSaleSampleCount,
                recentListingSampleCount,
                nowEpochMillis
        );
    }

    private long computeAverageSaleUnitPrice(List<SaleRecord> recentSales) {
        if (recentSales == null || recentSales.isEmpty()) {
            return 0L;
        }

        double total = 0.0;
        for (SaleRecord sale : recentSales) {
            total += sale.unitPrice();
        }

        return Math.max(0L, Math.round(total / recentSales.size()));
    }

    private long computeAverageListingUnitPrice(List<Listing> activeListings) {
        if (activeListings == null || activeListings.isEmpty()) {
            return 0L;
        }

        double weightedTotal = 0.0;
        long totalAmount = 0L;

        for (Listing listing : activeListings) {
            if (listing.amount() <= 0) {
                continue;
            }

            weightedTotal += (double) listing.unitPrice() * listing.amount();
            totalAmount += listing.amount();
        }

        if (totalAmount <= 0L) {
            return 0L;
        }

        return Math.max(0L, Math.round(weightedTotal / totalAmount));
    }

    private TrendData computeRecentSaleTrend(List<SaleRecord> recentSales) {
        if (recentSales == null || recentSales.size() < 2) {
            return new TrendData(0.0, 0.0);
        }

        List<SaleRecord> sorted = new ArrayList<>(recentSales);
        sorted.sort(Comparator.comparingLong(SaleRecord::soldAtEpochMillis));

        long firstTimestamp = sorted.get(0).soldAtEpochMillis();
        int count = sorted.size();
        double[] x = new double[count];
        double[] y = new double[count];

        for (int i = 0; i < count; i++) {
            SaleRecord sale = sorted.get(i);
            x[i] = (sale.soldAtEpochMillis() - firstTimestamp) / 3_600_000.0;
            y[i] = sale.unitPrice();
        }

        double meanX = average(x);
        double meanY = average(y);

        double sumXX = 0.0;
        double sumXY = 0.0;

        for (int i = 0; i < count; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            sumXX += dx * dx;
            sumXY += dx * dy;
        }

        if (sumXX <= 0.0) {
            return new TrendData(0.0, 0.0);
        }

        double slope = sumXY / sumXX;
        double intercept = meanY - slope * meanX;

        double ssTot = 0.0;
        double ssRes = 0.0;

        for (int i = 0; i < count; i++) {
            double predicted = intercept + slope * x[i];
            double actual = y[i];
            ssTot += Math.pow(actual - meanY, 2.0);
            ssRes += Math.pow(actual - predicted, 2.0);
        }

        double fitness = ssTot <= 0.0 ? 0.0 : clamp(1.0 - (ssRes / ssTot), 0.0, 1.0);
        return new TrendData(slope, fitness);
    }

    private long bootstrapRecommendation(long averageSaleUnitPrice, long averageListingUnitPrice) {
        if (averageSaleUnitPrice > 0L && averageListingUnitPrice > 0L) {
            return Math.max(0L, Math.round((averageSaleUnitPrice + averageListingUnitPrice) / 2.0));
        }
        if (averageSaleUnitPrice > 0L) {
            return averageSaleUnitPrice;
        }
        if (averageListingUnitPrice > 0L) {
            return averageListingUnitPrice;
        }
        return 0L;
    }

    private long calculateRecommendedUnitPrice(
            long currentRecommendation,
            long averageSaleUnitPrice,
            long averageListingUnitPrice,
            double recentSaleSlope,
            double recentSaleSlopeFitness,
            int recentSaleSampleCount,
            int recentListingSampleCount
    ) {
        if (currentRecommendation <= 0L) {
            return bootstrapRecommendation(averageSaleUnitPrice, averageListingUnitPrice);
        }

        long target = chooseTargetPrice(averageSaleUnitPrice, averageListingUnitPrice);
        if (target <= 0L) {
            return currentRecommendation;
        }

        double differencePercent = percentDifference(currentRecommendation, target);
        double baseAdjustmentPercent = classifyBaseAdjustmentPercent(currentRecommendation, target, differencePercent);

        if (baseAdjustmentPercent <= 0.0) {
            return currentRecommendation;
        }

        baseAdjustmentPercent *= trendAdjustmentMultiplier(
                currentRecommendation,
                target,
                recentSaleSlope,
                recentSaleSlopeFitness
        );

        baseAdjustmentPercent *= sampleConfidenceMultiplier(
                recentSaleSampleCount,
                recentListingSampleCount
        );

        double finalAdjustmentPercent = clamp(
                baseAdjustmentPercent,
                0.0,
                ConfigService.get().marketMaximumAdjustmentPercent()
        );

        long delta = Math.max(1L, Math.round(currentRecommendation * (finalAdjustmentPercent / 100.0)));
        long distanceToTarget = Math.abs(target - currentRecommendation);
        long movement = Math.min(delta, distanceToTarget);

        if (movement <= 0L) {
            return currentRecommendation;
        }

        if (target > currentRecommendation) {
            return currentRecommendation + movement;
        }
        return Math.max(1L, currentRecommendation - movement);
    }

    private long chooseTargetPrice(long averageSaleUnitPrice, long averageListingUnitPrice) {
        if (averageSaleUnitPrice > 0L && averageListingUnitPrice > 0L) {
            return Math.max(0L, Math.round((averageSaleUnitPrice * 0.7) + (averageListingUnitPrice * 0.3)));
        }
        if (averageSaleUnitPrice > 0L) {
            return averageSaleUnitPrice;
        }
        if (averageListingUnitPrice > 0L) {
            return averageListingUnitPrice;
        }
        return 0L;
    }

    private double classifyBaseAdjustmentPercent(long currentRecommendation, long target, double differencePercent) {
        double same = ConfigService.get().marketSamePercent();
        if (differencePercent <= same) {
            return 0.0;
        }

        double minAdjustment = ConfigService.get().marketMinimumAdjustmentPercent();
        double maxAdjustment = ConfigService.get().marketMaximumAdjustmentPercent();
        double small = ConfigService.get().marketSmallAdjustmentPercent();
        double medium = ConfigService.get().marketMediumAdjustmentPercent();

        if (differencePercent <= small) {
            return minAdjustment;
        }

        double mediumAdjustment = clamp(
                minAdjustment + Math.max(1.0, (maxAdjustment - minAdjustment) * 0.25),
                minAdjustment,
                maxAdjustment
        );

        if (differencePercent <= medium) {
            return mediumAdjustment;
        }

        boolean currentlyOverpriced = currentRecommendation > target;
        double majorThreshold = currentlyOverpriced
                ? ConfigService.get().marketMajorOverpricedPercent()
                : ConfigService.get().marketMajorUnderpricedPercent();

        if (differencePercent >= majorThreshold) {
            return maxAdjustment;
        }

        if (majorThreshold <= medium) {
            return maxAdjustment;
        }

        double t = (differencePercent - medium) / (majorThreshold - medium);
        t = clamp(t, 0.0, 1.0);

        return mediumAdjustment + (maxAdjustment - mediumAdjustment) * t;
    }

    private double trendAdjustmentMultiplier(
            long currentRecommendation,
            long target,
            double recentSaleSlope,
            double recentSaleSlopeFitness
    ) {
        if (recentSaleSlopeFitness < ConfigService.get().marketTrendFitnessThreshold()) {
            return 1.0;
        }

        boolean targetAboveCurrent = target > currentRecommendation;
        boolean slopeSupportsMove = (targetAboveCurrent && recentSaleSlope > 0.0)
                || (!targetAboveCurrent && recentSaleSlope < 0.0);

        double influence = clamp(recentSaleSlopeFitness * 0.25, 0.0, 0.25);
        return slopeSupportsMove ? 1.0 + influence : 1.0 - influence;
    }

    private double sampleConfidenceMultiplier(int recentSaleSampleCount, int recentListingSampleCount) {
        double saleConfidence = 0.0;
        double listingConfidence = 0.0;

        int minimumSaleSamples = ConfigService.get().marketMinimumSaleSamples();
        int minimumListingSamples = ConfigService.get().marketMinimumListingSamples();

        if (minimumSaleSamples > 0) {
            saleConfidence = clamp((double) recentSaleSampleCount / minimumSaleSamples, 0.0, 1.0);
        }

        if (minimumListingSamples > 0) {
            listingConfidence = clamp((double) recentListingSampleCount / minimumListingSamples, 0.0, 1.0);
        }

        double confidence = Math.max(saleConfidence, listingConfidence * 0.75);
        return 0.25 + (confidence * 0.75);
    }

    private double percentDifference(long currentValue, long comparisonValue) {
        if (comparisonValue <= 0L) {
            return 0.0;
        }
        return (Math.abs(currentValue - comparisonValue) / (double) comparisonValue) * 100.0;
    }

    private double average(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }

        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record TrendData(double slope, double fitness) {
    }
}
