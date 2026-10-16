package com.crediticio.auth.config;

import com.crediticio.auth.shared.response.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 15 * 60 * 1000;

    private final ConcurrentHashMap<String, LoginAttempt> attempts = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!isLoginEndpoint(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);
        LoginAttempt attempt = attempts.compute(clientIp, (key, existing) -> {
            if (existing == null || existing.isExpired()) {
                return new LoginAttempt();
            }
            return existing;
        });

        if (attempt.getCount() >= MAX_ATTEMPTS) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setStatus(429);
            ApiError error = ApiError.builder()
                    .errorCode("RATE_LIMIT_EXCEEDED")
                    .message("Demasiados intentos de login. Intente nuevamente en 15 minutos")
                    .traceId(UUID.randomUUID().toString())
                    .build();
            objectMapper.findAndRegisterModules();
            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        attempt.increment();
        filterChain.doFilter(request, response);

        if (response.getStatus() == 200) {
            attempts.remove(clientIp);
        }
    }

    private boolean isLoginEndpoint(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && "/api/v1/auth/login".equals(request.getRequestURI());
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwarded = request.getHeader("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.isBlank()) {
            return xForwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class LoginAttempt {
        private final AtomicInteger count = new AtomicInteger(0);
        private final long windowStart = System.currentTimeMillis();

        int getCount() {
            return count.get();
        }

        void increment() {
            count.incrementAndGet();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - windowStart > WINDOW_MS;
        }
    }
}
