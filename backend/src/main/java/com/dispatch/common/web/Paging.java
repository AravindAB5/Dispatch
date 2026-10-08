package com.dispatch.common.web;

/**
 * Guards against unbounded page sizes coming from query parameters.
 */
public final class Paging {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static int size(Integer requested) {
        if (requested == null || requested < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(requested, MAX_SIZE);
    }

    /** Capped so that page × size can never overflow the offset (which would surface as a 500). */
    public static final int MAX_PAGE = Integer.MAX_VALUE / MAX_SIZE;

    public static int page(Integer requested) {
        return requested == null || requested < 0 ? 0 : Math.min(requested, MAX_PAGE);
    }
}
