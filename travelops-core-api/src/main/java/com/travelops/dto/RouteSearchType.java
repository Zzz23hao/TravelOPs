package com.travelops.dto;

public enum RouteSearchType {
    SHORTEST("shortest"),
    CHEAPEST("cheapest"),
    MIN_TRANSFERS("min-transfers");

    private final String path;

    RouteSearchType(String path) {
        this.path = path;
    }

    /** The path segment used by both this API and the analytics API, e.g. "min-transfers". */
    public String path() {
        return path;
    }
}
