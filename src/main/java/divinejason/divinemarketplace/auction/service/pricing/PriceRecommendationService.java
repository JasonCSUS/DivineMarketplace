package divinejason.divinemarketplace.auction.service.pricing;


/*
 * File role: Defines the service contract for price recommendation service so command, GUI, and runtime code share one behavior boundary.
 */
public interface PriceRecommendationService {
    void reload();

    long getRecommendedUnitPrice(String marketKey);

    void forceRecalculate(String marketKey);

    int forceRecalculateAll(Iterable<String> marketKeys);

    void setManualRecommendedUnitPrice(String marketKey, long unitPrice);
}
