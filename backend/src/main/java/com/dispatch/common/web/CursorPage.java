package com.dispatch.common.web;

import java.util.List;

/**
 * Keyset-paginated slice. {@code nextCursor} is opaque to clients and is passed back verbatim.
 */
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {
}
