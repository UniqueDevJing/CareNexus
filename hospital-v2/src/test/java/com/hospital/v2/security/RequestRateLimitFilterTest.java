package com.hospital.v2.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestRateLimitFilterTest {
    @Test
    void login_is_limited_per_ip_and_window() throws Exception {
        RequestRateLimitFilter filter = new RequestRateLimitFilter(
                Clock.fixed(Instant.parse("2026-11-21T00:00:00Z"), ZoneOffset.UTC));
        FilterChain chain = (request, response) -> response.getWriter().write("ok");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v2/api/auth/login");
        request.setRemoteAddr("10.0.0.7");
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus());
        }
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request, limited, chain);
        assertEquals(429, limited.getStatus());
        assertTrue(limited.getContentAsString().contains("请求过于频繁"));
    }
}
