package divinejason.divinemarketplace.menu;


/*
 * File role: Stores the compact click payload attached to a rendered GUI slot; live data is re-fetched before mutations.
 */
import java.util.UUID;

/**
 * Immutable slot action stored with a rendered menu.
 *
 * Only ids/tokens are stored here. Business data must still be re-fetched from
 * live stores/services when an action mutates listings, claims, or money.
 */
public record MenuAction(
        MenuActionType type,
        MenuView targetView,
        String token,
        UUID uuid,
        int amount
) {
    public static MenuAction none() {
        return new MenuAction(MenuActionType.NONE, null, null, null, 0);
    }

    public static MenuAction simple(MenuActionType type) {
        return new MenuAction(type, null, null, null, 0);
    }

    public static MenuAction openView(MenuView view) {
        return new MenuAction(MenuActionType.OPEN_VIEW, view, null, null, 0);
    }

    public static MenuAction token(MenuActionType type, String token) {
        return new MenuAction(type, null, token, null, 0);
    }

    public static MenuAction uuid(MenuActionType type, UUID uuid) {
        return new MenuAction(type, null, null, uuid, 0);
    }

    public static MenuAction amount(MenuActionType type, int amount) {
        return new MenuAction(type, null, null, null, amount);
    }
}
