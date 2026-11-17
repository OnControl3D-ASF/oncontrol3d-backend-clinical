package com.oncontrol.oncontrolbackend.iam.infrastructure.filter;

import com.oncontrol.oncontrolbackend.iam.domain.model.User;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.CustomUserDetailsService;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private FilterChain chain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest request(String uri, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    @Test
    void withoutBearerHeader_passesThroughUnauthenticated() throws Exception {
        MockHttpServletRequest request = request("/api/symptoms", null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void publicRouteWithoutHeader_passesThrough() throws Exception {
        MockHttpServletRequest request = request("/api/auth/login", "Basic abc");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void validToken_authenticatesRequest() throws Exception {
        User user = User.builder().email("org@h.com").build();
        when(jwtService.extractUsername("good-token")).thenReturn("org@h.com");
        when(userDetailsService.loadUserByUsername("org@h.com")).thenReturn(user);
        when(jwtService.validateToken("good-token", "org@h.com")).thenReturn(true);
        MockHttpServletRequest request = request("/api/symptoms", "Bearer good-token");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(user);
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidToken_leavesRequestUnauthenticated() throws Exception {
        when(jwtService.extractUsername("bad-token")).thenReturn("org@h.com");
        when(userDetailsService.loadUserByUsername("org@h.com")).thenReturn(User.builder().build());
        when(jwtService.validateToken("bad-token", "org@h.com")).thenReturn(false);
        MockHttpServletRequest request = request("/api/symptoms", "Bearer bad-token");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void malformedToken_doesNotBreakTheChain() throws Exception {
        when(jwtService.extractUsername("garbage")).thenThrow(new IllegalArgumentException("malformed"));
        MockHttpServletRequest request = request("/api/symptoms", "Bearer garbage");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
