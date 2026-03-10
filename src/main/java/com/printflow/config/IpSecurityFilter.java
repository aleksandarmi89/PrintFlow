package com.printflow.config;

import com.printflow.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class IpSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IpSecurityFilter.class);

    private final RateLimitService rateLimitService;
    private final boolean enforceBanForAllPaths;

    public IpSecurityFilter(RateLimitService rateLimitService,
                            @Value("${app.rate-limit.ban-list.enforce-all:true}") boolean enforceBanForAllPaths) {
        this.rateLimitService = rateLimitService;
        this.enforceBanForAllPaths = enforceBanForAllPaths;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enforceBanForAllPaths) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        if (ip != null && !ip.isBlank() && !rateLimitService.isWhitelisted(ip) && rateLimitService.isBanned(ip)) {
            log.warn("[IP_BLOCKED] method={} path={} ip={}", request.getMethod(), request.getRequestURI(), ip);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Access denied.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
