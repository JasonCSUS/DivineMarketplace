package divinejason.divinemarketplace.menu;

import divinejason.divinemarketplace.auction.model.CategorySummary;
import divinejason.divinemarketplace.auction.model.ItemClaimRecord;
import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.auction.model.RecommendationHistoryPoint;
import divinejason.divinemarketplace.auction.model.SaleRecord;
import divinejason.divinemarketplace.auction.model.SortMode;
import divinejason.divinemarketplace.auction.model.SubcategorySummary;
import org.bukkit.inventory.ItemStack;

import java.time.YearMonth;

/**
 * Central GUI item builder.
 *
 * Keep all filler/button lore/icon creation in one place so renderer classes stay
 * focused on layout rather than item-construction noise.
 */
public final class MenuItemFactory {

    public ItemStack backButton() { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack previousPageButton() { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack nextPageButton() { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack searchButton() { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack claimEarningsButton(long balance) { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack sortButton(SortMode currentMode) { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack saleHistoryButton() { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack priceHistoryButton() { throw new UnsupportedOperationException("pseudocode scaffold"); }

    public ItemStack categoryItem(CategorySummary summary) { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack marketGroupItem(SubcategorySummary summary) { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack listingItem(Listing listing) { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack claimItem(ItemClaimRecord claim) { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack saleHistoryItem(SaleRecord sale) { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack priceHistoryItem(RecommendationHistoryPoint point) { throw new UnsupportedOperationException("pseudocode scaffold"); }

    public ItemStack quantityPaper(int quantity, long unitPrice, int availableAmount) { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack increaseArrow() { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack decreaseArrow() { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack confirmPurchaseButton(int quantity, long totalPrice) { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack cancelListingButton() { throw new UnsupportedOperationException("pseudocode scaffold"); }

    public ItemStack fillerPurple() { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack fillerBlack() { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack lockedRed() { throw new UnsupportedOperationException("pseudocode scaffold"); }
    public ItemStack monthContextItem(YearMonth month) { throw new UnsupportedOperationException("pseudocode scaffold"); }
}
