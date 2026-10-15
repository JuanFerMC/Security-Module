package com.crediticio.auth.config;

import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.roles.domain.Rol;
import com.crediticio.auth.roles.domain.RolSistema;
import com.crediticio.auth.roles.ports.output.RolRepositoryPort;
import com.crediticio.auth.users.domain.Usuario;
import com.crediticio.auth.users.ports.output.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private RolRepositoryPort rolRepository;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    @InjectMocks
    private AdminBootstrapRunner runner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(runner, "adminNombre", "Administrador");
    }

    private void configure(String email, String password) {
        ReflectionTestUtils.setField(runner, "adminEmail", email);
        ReflectionTestUtils.setField(runner, "adminPassword", password);
    }

    @Test
    void run_createsFirstAdminWhenNoneExists() {
        configure("  Primer@Empresa.com ", "claveInicial2026!");
        Rol rol = Rol.builder().id(1L).nombre(RolSistema.ADMINISTRADOR_RIESGO).build();
        when(userRepository.existsByRolNombre(RolSistema.ADMINISTRADOR_RIESGO)).thenReturn(false);
        when(rolRepository.findByNombre(RolSistema.ADMINISTRADOR_RIESGO)).thenReturn(Optional.of(rol));
        when(userRepository.existsByEmail("primer@empresa.com")).thenReturn(false);
        when(passwordEncoder.encode("claveInicial2026!")).thenReturn("hash");
        when(userRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(userRepository).save(captor.capture());
        assertEquals("primer@empresa.com", captor.getValue().getEmail());
        assertEquals("hash", captor.getValue().getPasswordHash());
        assertEquals("BOOTSTRAP", captor.getValue().getCreadoPor());
    }

    @Test
    void run_isDisabledOnceAnAdminExists() {
        configure("otro@empresa.com", "claveInicial2026!");
        when(userRepository.existsByRolNombre(RolSistema.ADMINISTRADOR_RIESGO)).thenReturn(true);

        runner.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_doesNothingWithoutConfiguration() {
        configure("", "");
        when(userRepository.existsByRolNombre(RolSistema.ADMINISTRADOR_RIESGO)).thenReturn(false);

        runner.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void run_rejectsShortPassword() {
        configure("primer@empresa.com", "corta123");
        when(userRepository.existsByRolNombre(RolSistema.ADMINISTRADOR_RIESGO)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(userRepository, never()).save(any());
    }
}
