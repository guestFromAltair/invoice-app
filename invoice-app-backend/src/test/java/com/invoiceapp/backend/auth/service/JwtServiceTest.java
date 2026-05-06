package com.invoiceapp.backend.auth.service;

import com.invoiceapp.backend.auth.domain.Role;
import com.invoiceapp.backend.auth.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService")
class JwtServiceTest {

    private JwtService jwtService;

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long TEST_EXPIRATION = 86400000L;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();

        var secretField = JwtService.class.getDeclaredField("secretKey");
        secretField.setAccessible(true);
        secretField.set(jwtService, TEST_SECRET);

        var expirationField = JwtService.class.getDeclaredField("jwtExpiration");
        expirationField.setAccessible(true);
        expirationField.set(jwtService, TEST_EXPIRATION);
    }

    private User buildTestUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();
    }

    @Test
    @DisplayName("should generate a non-blank token for a valid user")
    void should_generate_non_blank_token() {
        User user = buildTestUser();
        String token = jwtService.generateToken(user);

        assertThat(token)
                .isNotBlank()
                .contains(".")
                .satisfies(t -> assertThat(t.split("\\.")).hasSize(3));
    }

    @Test
    @DisplayName("should extract the correct username from a generated token")
    void should_extract_username_from_token() {
        User user = buildTestUser();
        String token = jwtService.generateToken(user);

        String extractedUsername = jwtService.extractUsername(token);

        assertThat(extractedUsername).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("should validate a freshly generated token as valid")
    void should_validate_fresh_token_as_valid() {
        User user = buildTestUser();
        String token = jwtService.generateToken(user);

        boolean isValid = jwtService.isTokenValid(token, user);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("should reject a token generated for a different user")
    void should_reject_token_for_wrong_user() {
        User user1 = buildTestUser();
        User user2 = User.builder()
                .id(UUID.randomUUID())
                .email("other@example.com")
                .password("hashed")
                .role(Role.USER)
                .build();

        String tokenForUser1 = jwtService.generateToken(user1);

        boolean isValid = jwtService.isTokenValid(tokenForUser1, user2);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("should reject an expired token")
    void should_reject_expired_token() throws Exception {
        var expirationField = JwtService.class.getDeclaredField("jwtExpiration");
        expirationField.setAccessible(true);
        expirationField.set(jwtService, -1000L);

        User user = buildTestUser();
        String expiredToken = jwtService.generateToken(user);

        assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken, user))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    @DisplayName("should reject a tampered token")
    void should_reject_tampered_token() {
        User user = buildTestUser();
        String token = jwtService.generateToken(user);

        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "TAMPERED" + "." + parts[2];

        assertThatThrownBy(() -> jwtService.isTokenValid(tamperedToken, user))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }
}