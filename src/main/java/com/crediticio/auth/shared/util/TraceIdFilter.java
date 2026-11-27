package com.crediticio.auth.shared.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Contrato tecnico comun de las tres API (scoring-api, evaluation-query-api y este modulo): reutiliza el
 * encabezado X-Trace-Id recibido si es un UUID valido o genera uno nuevo, lo deja en el MDC (los logs JSON lo
 * incluyen) y lo devuelve en la respuesta. Se ejecuta antes que Spring Security para que las respuestas
 * 401/403/429 tambien lleven el mismo traceId en el encabezado y en el cuerpo ApiError.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolverTraceId(request.getHeader(TRACE_ID_HEADER));
        try {
            MDC.put(MDC_KEY, traceId);
            response.setHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * traceId de la peticion en curso para el cuerpo ApiError. Fuera de una peticion filtrada (p. ej. pruebas
     * unitarias del manejador de errores) genera uno nuevo, igual que el comportamiento anterior.
     */
    public static String traceIdActual() {
        String traceId = MDC.get(MDC_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }

    private static String resolverTraceId(String traceIdRecibido) {
        if (traceIdRecibido != null) {
            try {
                return UUID.fromString(traceIdRecibido.trim()).toString();
            } catch (IllegalArgumentException ex) {
                // formato invalido: se genera uno nuevo
            }
        }
        return UUID.randomUUID().toString();
    }
}
