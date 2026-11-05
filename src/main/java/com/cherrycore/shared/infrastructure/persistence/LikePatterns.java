package com.cherrycore.shared.infrastructure.persistence;

import java.util.Locale;

/**
 * Patrones LIKE a partir de un texto de búsqueda. Los comodines que escribe el
 * usuario ({@code %}, {@code _}) y el carácter de escape se buscan como texto:
 * buscar "%" no debe devolver todo, ni "_" cualquier carácter.
 *
 * <p>Las consultas deben usar {@code LIKE :param ESCAPE '\'} (o el
 * {@link #ESCAPE} en Criteria).</p>
 */
public final class LikePatterns {

    public static final char ESCAPE = '\\';
    private static final String ANY = "%";

    private LikePatterns() {
    }

    /**
     * @return {@code %texto%} en minúsculas, con los comodines escapados
     */
    public static String containingLowercase(String search) {
        StringBuilder pattern = new StringBuilder(ANY);
        for (char c : search.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == '%' || c == '_' || c == ESCAPE) {
                pattern.append(ESCAPE);
            }
            pattern.append(c);
        }
        return pattern.append(ANY).toString();
    }
}
