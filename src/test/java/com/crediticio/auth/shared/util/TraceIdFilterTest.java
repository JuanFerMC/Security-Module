package com.crediticio.auth.shared.util;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TraceIdFilterTest {

    private final TraceIdFilter filtro = new TraceIdFilter();

    private String ejecutar(MockHttpServletRequest request, MockHttpServletResponse response,
                            AtomicReference<String> traceIdDuranteLaPeticion) throws Exception {
        filtro.doFilter(request, response, (req, res) -> traceIdDuranteLaPeticion.set(MDC.get(TraceIdFilter.MDC_KEY)));
        return response.getHeader(TraceIdFilter.TRACE_ID_HEADER);
    }

    @Test
    void reutilizaElTraceIdRecibidoSiEsUuidValido() throws Exception {
        String recibido = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, recibido);
        AtomicReference<String> enMdc = new AtomicReference<>();

        String devuelto = ejecutar(request, new MockHttpServletResponse(), enMdc);

        assertThat(devuelto).isEqualTo(recibido);
        assertThat(enMdc.get()).isEqualTo(recibido);
    }

    @Test
    void generaUnTraceIdNuevoSiNoSeRecibe() throws Exception {
        AtomicReference<String> enMdc = new AtomicReference<>();

        String devuelto = ejecutar(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(), enMdc);

        assertThatCode(() -> UUID.fromString(devuelto)).doesNotThrowAnyException();
        assertThat(enMdc.get()).isEqualTo(devuelto);
    }

    @Test
    void reemplazaUnTraceIdConFormatoInvalido() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "<script>no-es-uuid</script>");
        AtomicReference<String> enMdc = new AtomicReference<>();

        String devuelto = ejecutar(request, new MockHttpServletResponse(), enMdc);

        assertThat(devuelto).isNotEqualTo("<script>no-es-uuid</script>");
        assertThatCode(() -> UUID.fromString(devuelto)).doesNotThrowAnyException();
    }

    @Test
    void limpiaElMdcAlTerminarLaPeticion() throws Exception {
        ejecutar(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(), new AtomicReference<>());

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void traceIdActualGeneraUnoNuevoFueraDeUnaPeticion() {
        MDC.remove(TraceIdFilter.MDC_KEY);

        assertThatCode(() -> UUID.fromString(TraceIdFilter.traceIdActual())).doesNotThrowAnyException();
    }
}
