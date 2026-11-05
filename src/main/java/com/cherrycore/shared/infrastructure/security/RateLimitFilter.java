package com.cherrycore.shared.infrastructure.security;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

import com.cherrycore.shared.domain.error.CommonErrorCode;
import com.cherrycore.shared.infrastructure.config.CherryCoreProperties;
import com.cherrycore.shared.infrastructure.web.ApiErrorWriter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limita las peticiones que modifican, por IP y endpoint, en la API de
 * autenticación y de "mi cuenta" (login, registro, MFA, contraseña...), y
 * algunas lecturas (marca de la app en el login, descarga de mis datos). Frena ataques de fuerza bruta, credential stuffing y spam
 * de registros/emails. Responde 429 con la cabecera Retry-After.
 *
 * <p>Los contadores están en memoria: con varias instancias de CherryCore
 * habrá que moverlos a Redis (Bucket4j lo soporta sin cambiar este filtro).</p>
 *
 * <p>No es un {@code @Component} a propósito: solo se aplica a la cadena de
 * seguridad de la API de autenticación, no a todas las peticiones.</p>
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long MAX_TRACKED_KEYS = 100_000;
    private static final String KEY_SEPARATOR = "|";
    private static final Set<String> LIMITED_METHODS = Set.of(HttpMethod.POST.name(), HttpMethod.PUT.name(),
            HttpMethod.DELETE.name());
    private static final String IPV6_SEPARATOR = ":";
    private static final int IPV6_BYTES = 16;
    private static final int IPV6_PREFIX_BYTES = 8;
    private static final String IPV6_PREFIX_SUFFIX = "::/64";

    private final Cache<String, Bucket> buckets;
    private final int capacity;
    private final int readCapacity;
    private final Duration refillPeriod;
    private final ApiErrorWriter errorWriter;

    public RateLimitFilter(CherryCoreProperties.RateLimit settings, ApiErrorWriter errorWriter) {
        this.capacity = settings.capacity();
        this.readCapacity = settings.readCapacity();
        this.refillPeriod = settings.refillPeriod();
        this.errorWriter = errorWriter;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterAccess(refillPeriod.multipliedBy(2))
                .build();
    }

    /*
     * Peticiones que modifican en la API de autenticación (login, registro, MFA...)
     * y en "mi cuenta" (reautenticaciones: contraseña actual, códigos MFA).
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (limitedRead(request) != null) {
            return false;
        }
        boolean limitedPath = uri.startsWith(SecurityPaths.AUTH_API_PREFIX)
                || uri.startsWith(SecurityPaths.ACCOUNT_API_PREFIX);
        return !limitedPath || !LIMITED_METHODS.contains(request.getMethod());
    }

    /*
     * Lecturas limitadas. La marca de la app se agrupa por prefijo (no por
     * client_id): así no se pueden probar ids sin límite cambiando la URL.
     *
     * @return el grupo del contador, o null si no es una lectura limitada
     */
    static String limitedRead(HttpServletRequest request) {
        if (!HttpMethod.GET.name().equals(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith(SecurityPaths.APP_BRANDING_PREFIX)) {
            return SecurityPaths.APP_BRANDING_PREFIX;
        }
        return SecurityPaths.ACCOUNT_EXPORT.equals(uri) ? SecurityPaths.ACCOUNT_EXPORT : null;
    }

    /*
     * IPv6: un usuario suele tener un bloque /64 entero; se agrupa por bloque
     * para que no pueda saltarse el límite cambiando de dirección.
     */
    static String clientKey(String remoteAddress) {
        if (remoteAddress == null || !remoteAddress.contains(IPV6_SEPARATOR)) {
            return remoteAddress;
        }
        try {
            byte[] address = InetAddress.getByName(remoteAddress).getAddress();
            return address.length == IPV6_BYTES
                    ? HexFormat.of().formatHex(address, 0, IPV6_PREFIX_BYTES) + IPV6_PREFIX_SUFFIX
                    : remoteAddress;
        } catch (UnknownHostException notAnAddress) {
            return remoteAddress;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String readGroup = limitedRead(request);
        String path = readGroup == null ? request.getRequestURI() : readGroup;
        int limit = SecurityPaths.APP_BRANDING_PREFIX.equals(readGroup) ? readCapacity : capacity;
        String key = clientKey(request.getRemoteAddr()) + KEY_SEPARATOR + request.getMethod() + KEY_SEPARATOR + path;
        ConsumptionProbe probe = buckets.get(key, ignored -> newBucket(limit)).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        errorWriter.write(response, CommonErrorCode.TOO_MANY_REQUESTS);
    }

    private Bucket newBucket(int limit) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(limit).refillGreedy(limit, refillPeriod).build())
                .build();
    }
}
