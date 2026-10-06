package com.mcverse.jobify.common;

import com.mcverse.jobify.common.logging.CorrelationFilter;
import com.mcverse.jobify.config.SecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every request gets a correlation id in the logging context and in the response, and nothing leaks afterwards. */
class CorrelationFilterTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private String idSeenByTheApplication(MockHttpServletRequest request, CorrelationFilter filter) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(CorrelationFilter.REQUEST_ID));
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    private CorrelationFilter filter(boolean trustForwardedFor) {
        SecurityProperties properties = new SecurityProperties();
        properties.setTrustForwardedFor(trustForwardedFor);
        return new CorrelationFilter(properties);
    }

    @Test
    void generatesAnIdWhenTheCallerSendsNone() throws Exception {
        String seen = idSeenByTheApplication(new MockHttpServletRequest(), filter(false));
        assertTrue(seen.matches("[0-9a-f-]{36}"), seen);
        assertEquals(seen, response.getHeader("X-Request-Id"));
    }

    @Test
    void keepsASafeIdFromTheCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "front-end_123.abc");
        assertEquals("front-end_123.abc", idSeenByTheApplication(request, filter(false)));
        assertEquals("front-end_123.abc", response.getHeader("X-Request-Id"));
    }

    @Test
    void replacesAnUnsafeOrOversizedId() throws Exception {
        for (String bad : new String[] {"a b", "id\r\nforged: line", "x".repeat(65), "<script>", ""}) {
            MockHttpServletResponse fresh = new MockHttpServletResponse();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-Id", bad);
            AtomicReference<String> seen = new AtomicReference<>();
            filter(false).doFilter(request, fresh, (req, res) -> seen.set(MDC.get(CorrelationFilter.REQUEST_ID)));
            assertNotEquals(bad, seen.get());
            assertTrue(seen.get().matches("[0-9a-f-]{36}"), "replaced: " + bad);
        }
    }

    @Test
    void putsTheClientAddressInTheContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        AtomicReference<String> ip = new AtomicReference<>();
        filter(false).doFilter(request, response, (req, res) -> ip.set(MDC.get(CorrelationFilter.CLIENT_IP)));
        assertEquals("10.1.2.3", ip.get());
    }

    @Test
    void forwardedForIsUsedOnlyWhenTrusted() throws Exception {
        for (boolean trust : new boolean[] {false, true}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr("10.0.0.1");
            request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
            AtomicReference<String> ip = new AtomicReference<>();
            filter(trust).doFilter(request, new MockHttpServletResponse(),
                    (req, res) -> ip.set(MDC.get(CorrelationFilter.CLIENT_IP)));
            assertEquals(trust ? "203.0.113.9" : "10.0.0.1", ip.get());
        }
    }

    @Test
    void clearsTheContextEvenWhenTheRequestFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        try {
            filter(false).doFilter(request, response, (req, res) -> {
                MDC.put(CorrelationFilter.USER, "alice_s");
                throw new IllegalStateException("boom");
            });
        } catch (Exception expected) {
            // the filter must not swallow it
        }
        assertNull(MDC.get(CorrelationFilter.REQUEST_ID));
        assertNull(MDC.get(CorrelationFilter.CLIENT_IP));
        assertNull(MDC.get(CorrelationFilter.USER));
    }
}
