package com.invoiceapp.backend.auth.service;

import com.invoiceapp.backend.auth.domain.Role;
import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.shared.audit.AuditAction;
import com.invoiceapp.backend.shared.audit.AuditService;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;

    private static final String USER = "USER";

    public record RegisterRequest(String email, String password) {}
    public record LoginRequest(String email, String password) {}
    public record AuthResponse(String token, String email, String role) {}

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new InvoiceAppException("Email already registered", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();

        User saved = userRepository.save(user);

        auditService.log(
                USER,
                saved.getId(),
                AuditAction.USER_REGISTERED,
                null,
                Map.of("email", saved.getEmail(), "role", saved.getRole().name()),
                saved.getId()
        );

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        User user = userRepository.findByEmail(request.email()).orElseThrow();
        String token = jwtService.generateToken(user);

        auditService.log(
                USER,
                user.getId(),
                AuditAction.USER_LOGIN,
                null,
                Map.of("email", user.getEmail()),
                user.getId()
        );

        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }
}