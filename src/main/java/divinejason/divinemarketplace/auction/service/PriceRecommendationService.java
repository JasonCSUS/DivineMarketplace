package divinejason.divinemarketplace.auction.service;

public interface PriceRecommendationService {
    void reload();

    long getRecommendedUnitPrice(String marketKey);

    void forceRecalculate(String marketKey);

    int forceRecalculateAll(Iterable<String> marketKeys);

    void setManualRecommendedUnitPrice(String marketKey, long unitPrice);
}
