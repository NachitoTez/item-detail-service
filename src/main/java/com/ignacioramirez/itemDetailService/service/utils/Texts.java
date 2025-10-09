package com.ignacioramirez.itemDetailService.service.utils;

import java.text.Normalizer;
import java.util.Locale;

public final class Texts {
    private Texts() {}
    public static String normalizeTitle(String s) {
        if (s == null) return "";
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }
}
