package divinejason.divinemarketplace.auction.service;

/*
 * File role: Notification interface so business services can signal data-domain changes
 *            without a direct dependency on the GUI layer.
 */

/**
 * Implemented by the GUI data-version tracker.  Injected into listing, claim,
 * and purchase services so cache invalidation is triggered at the service layer
 * rather than in individual click handlers.
 */
public interface MarketChangeNotifier {

    void onListingsChanged();
    void onClaimsChanged();
    void onSalesChanged();

    /** No-op instance for tests and contexts where no GUI is attached. */
    MarketChangeNotifier NOOP = new MarketChangeNotifier() {
        @Override public void onListingsChanged() {}
        @Override public void onClaimsChanged()   {}
        @Override public void onSalesChanged()     {}
    };
}
