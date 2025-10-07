package com.ignacioramirez.itemDetailService.domain;

public record Picture(String url, boolean main, String alt) {
    public Picture {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("url required");
    }
}

