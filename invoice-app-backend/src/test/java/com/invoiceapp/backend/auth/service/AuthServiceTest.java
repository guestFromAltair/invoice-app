package com.invoiceapp.backend.auth.service;

import com.invoiceapp.backend.auth.domain.Role;
import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("should register a new user and return a token")
    void should_register_new_user_and_return_token() {
        when(jwtService.generateToken(any(User.class))).thenReturn("mocked.jwt.token");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashedpassword");
        when(userRepository.save(any(User.class)))
                .thenAnswer(inv -> {
                    User saved = inv.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });

        AuthService.AuthResponse response = authService.register(
                new AuthService.RegisterRequest("new@example.com", "password123")
        );

        assertThat(response.token()).isEqualTo("mocked.jwt.token");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.role()).isEqualTo("USER");

        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(argThat(user ->
                "$2a$10$hashedpassword".equals(user.getPassword())
        ));
    }

    @Test
    @DisplayName("should throw 409 when registering with an existing email")
    void should_throw_conflict_when_email_already_registered() {
        when(userRepository.findByEmail("existing@example.com"))
                .thenReturn(Optional.of(User.builder()
                        .email("existing@example.com")
                        .build()));

        assertThatThrownBy(() -> authService.register(
                new AuthService.RegisterRequest(
                        "existing@example.com", "password123"
                )
        ))
                .isInstanceOf(InvoiceAppException.class)
                .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should never store a plain text password")
    void should_never_store_plain_text_password() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new AuthService.RegisterRequest("user@example.com", "mypassword"));

        verify(userRepository).save(argThat(user -> !"mypassword".equals(user.getPassword())));
    }

    @Test
    @DisplayName("should login successfully and return a token")
    void should_login_successfully_and_return_token() {
        when(jwtService.generateToken(any(User.class))).thenReturn("mocked.jwt.token");

        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .password("$2a$10$hashed")
                .role(Role.USER)
                .build();

        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(
                        existingUser, null, existingUser.getAuthorities()
                ));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existingUser));

        AuthService.AuthResponse response = authService.login(
                new AuthService.LoginRequest("user@example.com", "password123")
        );

        assertThat(response.token()).isEqualTo("mocked.jwt.token");
        assertThat(response.email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("should throw BadCredentialsException for wrong password")
    void should_throw_for_wrong_password() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(
                new AuthService.LoginRequest("user@example.com", "wrongpassword")
        )).isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any(User.class));
    }

    @Test
    @DisplayName("should assign USER role by default on registration")
    void should_assign_user_role_by_default() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthService.AuthResponse response = authService.register(
                new AuthService.RegisterRequest("new@example.com", "password123")
        );

        assertThat(response.role()).isEqualTo("USER");
    }
}