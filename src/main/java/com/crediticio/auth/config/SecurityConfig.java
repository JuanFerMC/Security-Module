package com.crediticio.auth.config;

import com.crediticio.auth.shared.response.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.UUID;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Públicos
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // refresh y logout se autentican con el refresh token del cuerpo (el access token puede haber vencido)
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // Solo ADMINISTRADOR_RIESGO
                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios").hasRole("ADMINISTRADOR_RIESGO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios").hasRole("ADMINISTRADOR_RIESGO")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/usuarios/*/estado").hasRole("ADMINISTRADOR_RIESGO")
                        // ADMINISTRADOR_RIESGO o ANALISTA_CREDITO
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/usuarios/*/password").hasAnyRole("ADMINISTRADOR_RIESGO", "ANALISTA_CREDITO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/validate").hasAnyRole("ADMINISTRADOR_RIESGO", "ANALISTA_CREDITO")
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios/*").hasAnyRole("ADMINISTRADOR_RIESGO", "ANALISTA_CREDITO")
                        // Todo lo demás requiere autenticación
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(401);
                            ApiError error = ApiError.builder()
                                    .errorCode("UNAUTHORIZED")
                                    .message("Autenticación requerida")
                                    .traceId(UUID.randomUUID().toString())
                                    .build();
                            response.getWriter().write(objectMapper.writeValueAsString(error));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setStatus(403);
                            ApiError error = ApiError.builder()
                                    .errorCode("ACCESS_DENIED")
                                    .message("No tiene permisos para acceder a este recurso")
                                    .traceId(UUID.randomUUID().toString())
                                    .build();
                            response.getWriter().write(objectMapper.writeValueAsString(error));
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
