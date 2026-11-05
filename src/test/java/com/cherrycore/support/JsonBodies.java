package com.cherrycore.support;

import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

public final class JsonBodies {

    private JsonBodies() {
    }

    public static <T> T read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }
}
