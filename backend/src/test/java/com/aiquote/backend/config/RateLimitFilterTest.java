package com.aiquote.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/**
 * Covers Etap 21's addition of a tighter, separate limit for /api/auth/** (login/
 * register — previously unprotected against brute-force/registration spam) alongside
 * the pre-existing /api/public/** limit, and confirms every other path bypasses the
 * filter untouched.
 */
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @Test
    void allowsRequestsUnderThePublicLimit() throws Exception {
        for (int i = 0; i < 30; i++) {
            assertThat(callFilter("/api/public/companies/acme", "1.1.1.1")).isEqualTo(200);
        }
    }

    @Test
    void blocksThe31stPublicRequestFromTheSameIpWithinTheWindow() throws Exception {
        for (int i = 0; i < 30; i++) {
            callFilter("/api/public/companies/acme", "2.2.2.2");
        }
        assertThat(callFilter("/api/public/companies/acme", "2.2.2.2")).isEqualTo(429);
    }

    @Test
    void authEndpointsGetATighterLimitThanPublicEndpoints() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(callFilter("/api/auth/login", "3.3.3.3")).isEqualTo(200);
        }
        assertThat(callFilter("/api/auth/login", "3.3.3.3")).isEqualTo(429);
    }

    @Test
    void limitsAreTrackedSeparatelyPerIp() throws Exception {
        for (int i = 0; i < 10; i++) {
            callFilter("/api/auth/login", "4.4.4.4");
        }
        assertThat(callFilter("/api/auth/login", "4.4.4.4")).isEqualTo(429);
        assertThat(callFilter("/api/auth/login", "5.5.5.5")).isEqualTo(200);
    }

    @Test
    void authenticatedApiPathsAreNeverRateLimitedByThisFilter() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertThat(callFilter("/api/quotes", "6.6.6.6")).isEqualTo(200);
        }
    }

    private int callFilter(String uri, String remoteAddr) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn(uri);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        int[] status = {200};
        org.mockito.Mockito.doAnswer(invocation -> {
            status[0] = invocation.getArgument(0);
            return null;
        }).when(response).setStatus(any(Integer.class));
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, chain);

        if (status[0] == 429) {
            verify(chain, never()).doFilter(request, response);
        } else {
            verify(chain, times(1)).doFilter(request, response);
        }
        return status[0];
    }
}
