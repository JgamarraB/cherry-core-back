package com.cherrycore.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Simula el backend de una app (BFF) que usa CherryCore para el login:
 * construye la URL de autorización con PKCE y canjea el código por tokens.
 */
public class OAuthClientSimulator {

    public static final String AUTHORIZE_PATH = "/oauth2/authorize";
    public static final String TOKEN_PATH = "/oauth2/token";
    public static final String STATE = "state-123";

    private final MockMvc mockMvc;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String codeVerifier;

    public OAuthClientSimulator(MockMvc mockMvc, String clientId, String clientSecret, String redirectUri) {
        this.mockMvc = mockMvc;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        this.codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public String authorizationUrl(String scopes) throws Exception {
        return UriComponentsBuilder.fromUriString("http://localhost" + AUTHORIZE_PATH)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("scope", scopes)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", STATE)
                .queryParam("code_challenge", codeChallenge())
                .queryParam("code_challenge_method", "S256")
                .encode()
                .build()
                .toUriString();
    }

    public static String codeFrom(String redirectLocation) {
        return UriComponentsBuilder.fromUri(URI.create(redirectLocation)).build()
                .getQueryParams().getFirst("code");
    }

    public MvcResult exchangeCode(String code) throws Exception {
        return mockMvc.perform(post(TOKEN_PATH)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "authorization_code")
                .param("code", code)
                .param("redirect_uri", redirectUri)
                .param("code_verifier", codeVerifier))
                .andReturn();
    }

    public MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post(TOKEN_PATH)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "refresh_token")
                .param("refresh_token", refreshToken))
                .andReturn();
    }

    private String basicAuth() {
        String credentials = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String codeChallenge() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}
