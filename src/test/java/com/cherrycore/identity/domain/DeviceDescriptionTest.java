package com.cherrycore.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DeviceDescriptionTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "null", value = {
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36|Chrome|macOS",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36 Edg/126.0|Edge|Windows",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1|Safari|iOS",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36|Chrome|Android",
        "Mozilla/5.0 (X11; Linux x86_64; rv:128.0) Gecko/20100101 Firefox/128.0|Firefox|Linux",
        "curl/8.7.1|null|null"
    })
    void describesCommonBrowsers(String userAgent, String browser, String os) {
        assertThat(DeviceDescription.fromUserAgent(userAgent)).isEqualTo(new DeviceDescription(browser, os));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {"null", "''"})
    void unknownWithoutUserAgent(String userAgent) {
        assertThat(DeviceDescription.fromUserAgent(userAgent).isKnown()).isFalse();
    }
}
