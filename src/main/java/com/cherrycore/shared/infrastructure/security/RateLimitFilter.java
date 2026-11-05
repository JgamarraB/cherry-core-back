package com.cherrycore.shared.infrastructure.security;

import java.io.IOException;
import java.time.Duration;
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
 * Limita las peticiones POST por IP y endpoint (login, registro, olvidé mi
 * contraseña...). Frena ataques de fuerza bruta, credential stuffing y spam
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

    private final Cache<String, Bucket> buckets;
    private final int capacity;
    private final Duration refillPeriod;
    private final ApiErrorWriter errorWriter;

    public RateLimitFilter(CherryCoreProperties.RateLimit settings, ApiErrorWriter errorWriter) {
        this.capacity = settings.capacity();
        this.refillPeriod = settings.refillPeriod();
        this.errorWriter = errorWriter;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(MAX_TRACKED_KEYS)
                .expireAfterAccess(refillPeriod.multipliedBy(2))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + KEY_SEPARATOR + request.getRequestURI();
        ConsumptionProbe probe = buckets.get(key, ignored -> newBucket()).tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        errorWriter.write(response, CommonErrorCode.TOO_MANY_REQUESTS);
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(capacity).refillGreedy(capacity, refillPeriod).build())
                .build();
    }
}
