package com.cherrycore.identity.domain;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Navegador y sistema aproximados a partir del User-Agent ("Chrome", "macOS"),
 * para los avisos y la actividad de la cuenta. Sencillo a propósito: no
 * identifica versiones ni modelos. {@code null} en lo que no se reconoce.
 */
public record DeviceDescription(String browser, String os) {

    public static final DeviceDescription UNKNOWN = new DeviceDescription(null, null);

    /* El orden importa: Edge y Opera se anuncian también como Chrome; Chrome, como Safari. */
    private static final List<Map.Entry<Pattern, String>> BROWSERS = List.of(
            Map.entry(Pattern.compile("Edg(e|A|iOS)?/"), "Edge"),
            Map.entry(Pattern.compile("OPR/|Opera"), "Opera"),
            Map.entry(Pattern.compile("SamsungBrowser/"), "Samsung Internet"),
            Map.entry(Pattern.compile("Firefox/|FxiOS/"), "Firefox"),
            Map.entry(Pattern.compile("Chrome/|CriOS/|Chromium/"), "Chrome"),
            Map.entry(Pattern.compile("Version/[\\d.]+.*Safari/"), "Safari"));

    private static final List<Map.Entry<Pattern, String>> SYSTEMS = List.of(
            Map.entry(Pattern.compile("iPhone|iPad|iPod"), "iOS"),
            Map.entry(Pattern.compile("Android"), "Android"),
            Map.entry(Pattern.compile("Windows"), "Windows"),
            Map.entry(Pattern.compile("CrOS"), "ChromeOS"),
            Map.entry(Pattern.compile("Macintosh|Mac OS X"), "macOS"),
            Map.entry(Pattern.compile("Linux"), "Linux"));

    public static DeviceDescription fromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        return new DeviceDescription(firstMatch(BROWSERS, userAgent), firstMatch(SYSTEMS, userAgent));
    }

    public boolean isKnown() {
        return browser != null || os != null;
    }

    private static String firstMatch(List<Map.Entry<Pattern, String>> candidates, String userAgent) {
        return candidates.stream()
                .filter(candidate -> candidate.getKey().matcher(userAgent).find())
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
