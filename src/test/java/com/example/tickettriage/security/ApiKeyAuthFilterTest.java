package com.example.tickettriage.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApiKeyAuthFilter} itself — the integration test only
 * proves that an unauthenticated request is rejected, not which credentials
 * the filter accepts or ignores. These tests pin the exact contract:
 * only a non-blank, exactly matching key in the configured header
 * authenticates, and every other case leaves the context anonymous so the
 * entry point rejects the request.
 */
class ApiKeyAuthFilterTest {

    private static final String HEADER = "X-API-Key";
    private static final String KEY = "correct-horse-battery-staple";

    private AppSecurityProperties properties;
    private ApiKeyAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        properties = new AppSecurityProperties();
        properties.setApiKey(KEY);
        properties.setHeaderName(HEADER);
        filter = new ApiKeyAuthFilter(properties);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesWhenHeaderMatchesKeyExactly() throws Exception {
        when(request.getHeader(HEADER)).thenReturn(KEY);

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_API");
        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesContextAnonymousWhenHeaderIsMissing() throws Exception {
        when(request.getHeader(HEADER)).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesContextAnonymousWhenKeyIsWrong() throws Exception {
        when(request.getHeader(HEADER)).thenReturn("wrong-key");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesContextAnonymousWhenKeyDiffersOnlyByTrailingWhitespace() throws Exception {
        when(request.getHeader(HEADER)).thenReturn(KEY + " ");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void leavesContextAnonymousWhenHeaderIsBlank() throws Exception {
        when(request.getHeader(HEADER)).thenReturn("   ");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void readsOnlyTheConfiguredHeaderName() throws Exception {
        properties.setHeaderName("X-Custom-Key");
        when(request.getHeader("X-Custom-Key")).thenReturn(KEY);
        when(request.getHeader(HEADER)).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void ignoresKeySentInTheDefaultHeaderWhenCustomHeaderIsConfigured() throws Exception {
        properties.setHeaderName("X-Custom-Key");
        when(request.getHeader("X-Custom-Key")).thenReturn(null);
        when(request.getHeader(HEADER)).thenReturn(KEY);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
