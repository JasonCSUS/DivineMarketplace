package divinejason.divinemarketplace.menu.template;

/*
 * Layer : gui/template
 * Owns  : quantity selector, confirm-purchase, and cancel-listing button cluster for ItemDetail
 */

import divinejason.divinemarketplace.auction.model.Listing;
import divinejason.divinemarketplace.menu.MenuAction;
import divinejason.divinemarketplace.menu.MenuActionType;
import divinejason.divinemarketplace.menu.MenuSlots;
import java.util.Map;
import org.bukkit.inventory.Inventory;

/**
 * Renders the purchase quantity selector and action buttons for the item-detail screen.
 *
 * <p>When {@code canBuy} is {@code true}: decrease arrow, quantity paper, increase arrow,
 * and confirm-purchase button are placed. When {@code false}: owner-listing info is shown
 * in the quantity slot instead.</p>
 *
 * <p>When {@code canCancel} is {@code true}: the cancel-listing button is placed regardless
 * of {@code canBuy}.</p>
 */
public final class ConfirmCancelTemplate implements MenuSubTemplate {

    private final Listing listing;
    private final int selectedQuantity;
    private final boolean canBuy;
    private final boolean canCancel;

    public ConfirmCancelTemplate(Listing listing, int selectedQuantity, boolean canBuy, boolean canCancel) {
        this.listing = listing;
        this.selectedQuantity = selectedQuantity;
        this.canBuy = canBuy;
        this.canCancel = canCancel;
    }

    @Override
    public void apply(Inventory inventory, Map<Integer, MenuAction> actions, MenuRenderContext ctx) {
        int quantity = Math.max(1, Math.min(selectedQuantity, listing.amount()));

        if (canBuy) {
            MenuSubTemplate.put(inventory, actions,
                    ctx.visuals().slot("detail.decrease", MenuSlots.ITEM_DETAIL_DECREASE),
                    ctx.itemFactory().decreaseArrow(),
                    MenuAction.simple(MenuActionType.QUANTITY_DECREASE));
            MenuSubTemplate.put(inventory, actions,
                    ctx.visuals().slot("detail.quantity", MenuSlots.ITEM_DETAIL_QUANTITY),
                    ctx.itemFactory().quantityPaper(quantity, listing.unitPrice(), listing.amount()),
                    MenuAction.simple(MenuActionType.NONE));
            MenuSubTemplate.put(inventory, actions,
                    ctx.visuals().slot("detail.increase", MenuSlots.ITEM_DETAIL_INCREASE),
                    ctx.itemFactory().increaseArrow(),
                    MenuAction.simple(MenuActionType.QUANTITY_INCREASE));
            MenuSubTemplate.put(inventory, actions,
                    ctx.visuals().slot("detail.confirm", MenuSlots.ITEM_DETAIL_CONFIRM),
                    ctx.itemFactory().confirmPurchaseButton(quantity, listing.unitPrice() * quantity),
                    MenuAction.simple(MenuActionType.CONFIRM_PURCHASE));
        } else {
            MenuSubTemplate.put(inventory, actions,
                    ctx.visuals().slot("detail.quantity", MenuSlots.ITEM_DETAIL_QUANTITY),
                    ctx.itemFactory().ownerListingInfo(listing),
                    MenuAction.simple(MenuActionType.NONE));
        }

        if (canCancel) {
            MenuSubTemplate.put(inventory, actions,
                    ctx.visuals().slot("detail.cancel", MenuSlots.ITEM_DETAIL_CANCEL),
                    ctx.itemFactory().cancelListingButton(),
                    MenuAction.simple(MenuActionType.CANCEL_LISTING));
        }
    }
}
