package com.invoiceapp.backend.auth.controller;

import com.invoiceapp.backend.auth.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(
                new AuthService.RegisterRequest(request.email(), request.password())
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthService.AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(
                authService.login(
                        new AuthService.LoginRequest(request.email(), request.password())
                )
        );
    }

    public record RegisterRequest(
            @Email(message = "Must be a valid email address")
            @NotBlank(message = "Email is required")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password
    ) {}

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password
    ) {}
}