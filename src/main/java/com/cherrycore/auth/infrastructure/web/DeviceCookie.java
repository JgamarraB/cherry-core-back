package com.cherrycore.auth.infrastructure.web;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cookie de larga duración con un id aleatorio del navegador, para reconocer
 * los dispositivos conocidos de una cuenta. HttpOnly, SameSite=Lax y Secure
 * (salvo en local, como la de sesión). No identifica a nadie por sí sola: en
 * la base de datos solo hay un HMAC de cuenta + id.
 */
@Component
class DeviceCookie {

    private static final int ID_BYTES = 32;
    /** 32 bytes en Base64 URL sin relleno. Cualquier otro valor se ignora y se genera uno nuevo. */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final String SAME_SITE = "Lax";

    private final SecureRandom random = new SecureRandom();
    private final CherryCoreProperties.NewDeviceAlert settings;
    private final boolean secure;

    DeviceCookie(CherryCoreProperties properties) {
        this.settings = properties.security().newDeviceAlert();
        this.secure = properties.security().session().secureCookie();
    }

    /**
     * Id del navegador de la petición; si no tiene (o no es válido), uno nuevo.
     * Se vuelve a escribir para alargar su caducidad en cada login.
     */
    String resolve(HttpServletRequest request, HttpServletResponse response) {
        String id = read(request);
        if (id == null) {
            byte[] bytes = new byte[ID_BYTES];
            random.nextBytes(bytes);
            id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        ResponseCookie cookie = ResponseCookie.from(settings.cookieName(), id)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path("/")
                .maxAge(settings.cookieMaxAge())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return id;
    }

    private String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (settings.cookieName().equals(cookie.getName()) && cookie.getValue() != null
                    && VALID_ID.matcher(cookie.getValue()).matches()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
