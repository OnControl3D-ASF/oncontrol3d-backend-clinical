package com.oncontrol.oncontrolbackend.iam.infrastructure.filter;

import com.oncontrol.oncontrolbackend.iam.infrastructure.security.AuthenticatedPrincipal;
import com.oncontrol.oncontrolbackend.iam.infrastructure.security.PrincipalRole;
import com.oncontrol.oncontrolbackend.iam.infrastructure.service.JwtService;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The filter is stateless: it depends on JwtService only (no repository / UserDetailsService). */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private FilterChain chain;

    private JwtAuthenticationFilter filter;

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtService);
    }

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
    void nonBearerScheme_passesThrough() throws Exception {
        MockHttpServletRequest request = request("/api/auth/login", "Basic abc");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void validToken_authenticatesWithPrincipalAndRoleAuthority() throws Exception {
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                PrincipalRole.DOCTOR, "doc@h.com", 1L, 5L, 7L, null);
        when(jwtService.parsePrincipal("good-token")).thenReturn(principal);
        MockHttpServletRequest request = request("/api/symptoms", "Bearer good-token");

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isSameAs(principal);
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_DOCTOR");
        verify(chain).doFilter(request, response);
    }

    @Test
    void organizationToken_getsOrganizationAuthority() throws Exception {
        when(jwtService.parsePrincipal("org-token")).thenReturn(
                new AuthenticatedPrincipal(PrincipalRole.ORGANIZATION, "org@h.com", 1L, null, null, null));

        filter.doFilter(request("/api/organizations/1", "Bearer org-token"), response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_ORGANIZATION");
    }

    @Test
    void invalidToken_leavesRequestUnauthenticated() throws Exception {
        when(jwtService.parsePrincipal("bad-token")).thenThrow(new SignatureException("bad signature"));
        MockHttpServletRequest request = request("/api/symptoms", "Bearer bad-token");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void tokenWithMissingClaims_leavesRequestUnauthenticated() throws Exception {
        when(jwtService.parsePrincipal("no-claims")).thenThrow(new JwtException("missing role"));
        MockHttpServletRequest request = request("/api/symptoms", "Bearer no-claims");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void malformedToken_doesNotBreakTheChain() throws Exception {
        when(jwtService.parsePrincipal("garbage")).thenThrow(new IllegalArgumentException("malformed"));
        MockHttpServletRequest request = request("/api/symptoms", "Bearer garbage");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }
}
