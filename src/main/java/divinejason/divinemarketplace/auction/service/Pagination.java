package divinejason.divinemarketplace.auction.service;

import java.util.List;

/**
 * Small shared pagination helper for chat commands and menu-facing lists.
 *
 * Page numbers are one-based at the command/UI edge. Services that already use
 * zero-based pages can keep doing so internally, but new command/menu code should
 * prefer this helper to avoid repeated off-by-one logic.
 */
public final class Pagination {
    private Pagination() {
    }

    public static <T> PageSlice<T> sliceOneBased(List<T> items, int requestedPage, int pageSize) {
        List<T> safeItems = items == null ? List.of() : items;
        int safePageSize = Math.max(1, pageSize);
        int totalItems = safeItems.size();
        int totalPages = Math.max(1, (int) Math.ceil(totalItems / (double) safePageSize));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int start = Math.max(0, (page - 1) * safePageSize);
        int end = Math.min(totalItems, start + safePageSize);
        List<T> pageItems = start >= totalItems ? List.of() : List.copyOf(safeItems.subList(start, end));
        return new PageSlice<>(pageItems, page, safePageSize, totalItems, totalPages, page > 1, page < totalPages);
    }

    public record PageSlice<T>(
            List<T> items,
            int page,
            int pageSize,
            int totalItems,
            int totalPages,
            boolean hasPrevious,
            boolean hasNext
    ) {
        public boolean isEmpty() {
            return items.isEmpty();
        }
    }
}
