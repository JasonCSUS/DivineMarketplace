package divinejason.divinemarketplace.menu.template;

/*
 * Layer : gui/template
 * Owns  : player earnings/balance button in the screen header row
 */

import divinejason.divinemarketplace.menu.MenuAction;
import divinejason.divinemarketplace.menu.MenuActionType;
import java.util.Map;
import org.bukkit.inventory.Inventory;

/**
 * Places the player earnings button at a configurable header slot.
 *
 * <p>Used by both the main menu ({@code "main.claimEarnings"} /
 * {@link divinejason.divinemarketplace.menu.MenuSlots#MAIN_CLAIM_EARNINGS}) and the claims
 * screen ({@code "claims.earnings"} /
 * {@link divinejason.divinemarketplace.menu.MenuSlots#CLAIMS_EARNINGS}). The slot config key
 * and fallback slot are provided at construction time so the same template class covers both
 * placements.</p>
 */
public final class PlayerSummaryHeaderTemplate implements MenuSubTemplate {

    private final long playerBalanceHundredths;
    private final String slotConfigKey;
    private final int fallbackSlot;

    public PlayerSummaryHeaderTemplate(long playerBalanceHundredths, String slotConfigKey, int fallbackSlot) {
        this.playerBalanceHundredths = playerBalanceHundredths;
        this.slotConfigKey = slotConfigKey;
        this.fallbackSlot = fallbackSlot;
    }

    @Override
    public void apply(Inventory inventory, Map<Integer, MenuAction> actions, MenuRenderContext ctx) {
        MenuSubTemplate.put(inventory, actions,
                ctx.visuals().slot(slotConfigKey, fallbackSlot),
                ctx.itemFactory().claimEarningsButton(playerBalanceHundredths),
                MenuAction.simple(MenuActionType.CLAIM_EARNINGS));
    }
}
