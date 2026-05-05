package divinejason.divinemarketplace.menu;

import java.util.List;

/**
 * One rendered menu page plus enough boundary metadata to avoid dead next/previous buttons.
 */
public record MenuPage<T>(
        List<T> items,
        int page,
        boolean hasPrevious,
        boolean hasNext
) {
    public MenuPage {
        items = List.copyOf(items);
        page = Math.max(0, page);
    }

    public static <T> MenuPage<T> of(List<T> items, int page, boolean hasNext) {
        int safePage = Math.max(0, page);
        return new MenuPage<>(items, safePage, safePage > 0, hasNext);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
