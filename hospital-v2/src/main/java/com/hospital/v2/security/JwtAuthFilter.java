package com.hospital.v2.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final TokenService tokens;

    public JwtAuthFilter(TokenService tokens) { this.tokens = tokens; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : cookieToken(req);
        if (token != null && !token.isBlank()) {
            try {
                AuthPrincipal p = tokens.verify(token);
                var a = new UsernamePasswordAuthenticationToken(p, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + p.role().toUpperCase())));
                SecurityContextHolder.getContext().setAuthentication(a);
            } catch (IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }

    private String cookieToken(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (var cookie : req.getCookies()) {
            if ("hospital_session".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
