package com.cherrycore.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.cherrycore.identity.infrastructure.web.AuthPaths;
import com.cherrycore.shared.infrastructure.security.CsrfSettings;

import jakarta.servlet.http.Cookie;

/**
 * Simula un navegador: guarda las cookies entre peticiones (sesión y CSRF)
 * y envía la cabecera CSRF como haría el frontend.
 */
public class BrowserSession {

    private final MockMvc mockMvc;
    private final Map<String, Cookie> cookies = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String userAgent = "cherrycore-tests";

    public BrowserSession(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    public MvcResult get(String url, MediaType accept) throws Exception {
        return perform(MockMvcRequestBuilders.get(URI.create(url)).accept(accept));
    }

    /**
     * POST JSON a la API de login, con CSRF.
     */
    public MvcResult postJson(String path, String json) throws Exception {
        ensureCsrfCookie();
        return perform(post(AuthPaths.BASE + path)
                .contentType(MediaType.APPLICATION_JSON)
                .header(CsrfSettings.HEADER_NAME, cookies.get(CsrfSettings.COOKIE_NAME).getValue())
                .content(json));
    }

    /**
     * Petición JSON a cualquier ruta de la API del frontend (con CSRF en los
     * métodos que modifican).
     */
    public MvcResult call(HttpMethod method, String path, String json) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, path);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        if (!HttpMethod.GET.equals(method)) {
            ensureCsrfCookie();
            request.header(CsrfSettings.HEADER_NAME, cookies.get(CsrfSettings.COOKIE_NAME).getValue());
        }
        return perform(request);
    }

    public MvcResult get(String path) throws Exception {
        return call(HttpMethod.GET, path, null);
    }

    public MvcResult upload(String path, String partName, byte[] content) throws Exception {
        ensureCsrfCookie();
        MockMultipartHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart(HttpMethod.PUT, path)
                .file(new MockMultipartFile(partName, "file.bin", MediaType.APPLICATION_OCTET_STREAM_VALUE, content))
                .header(CsrfSettings.HEADER_NAME, cookies.get(CsrfSettings.COOKIE_NAME).getValue());
        return perform(request);
    }

    /**
     * POST JSON sin cabecera CSRF (para comprobar que se rechaza).
     */
    public MvcResult postJsonWithoutCsrf(String path, String json) throws Exception {
        return perform(post(AuthPaths.BASE + path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public MvcResult login(String email, String password) throws Exception {
        return postJson(AuthPaths.CUSTOMER_LOGIN, """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password));
    }

    /**
     * Cabecera que se enviará en todas las peticiones (p. ej. Accept-Language).
     */
    public BrowserSession withHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /**
     * Navegador que dice ser (cabecera User-Agent).
     */
    public BrowserSession withUserAgent(String value) {
        userAgent = value;
        return this;
    }

    public Cookie cookie(String name) {
        return cookies.get(name);
    }

    public boolean hasCookie(String name) {
        return cookies.containsKey(name);
    }

    private void ensureCsrfCookie() throws Exception {
        if (!cookies.containsKey(CsrfSettings.COOKIE_NAME)) {
            perform(MockMvcRequestBuilders.get(AuthPaths.BASE + AuthPaths.CSRF));
        }
    }

    private MvcResult perform(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        if (!cookies.isEmpty()) {
            request.cookie(cookies.values().toArray(Cookie[]::new));
        }
        request.header(HttpHeaders.USER_AGENT, userAgent);
        headers.forEach(request::header);
        MvcResult result = mockMvc.perform(request).andReturn();
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (cookie.getMaxAge() == 0) {
                cookies.remove(cookie.getName());
            } else {
                cookies.put(cookie.getName(), cookie);
            }
        }
        return result;
    }
}
