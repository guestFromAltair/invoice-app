package com.invoiceapp.backend.shared.security;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CurrentUserResolver Unit Tests")
class CurrentUserResolverTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserResolver currentUserResolver;

    private SecurityContext securityContext;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        securityContext = mock(SecurityContext.class);
        authentication = mock(Authentication.class);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("should resolve user when authenticated user exists in database")
    void should_resolve_user_successfully() {
        String email = "test@invoiceapp.com";
        User user = User.builder().email(email).build();

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        User resolved = currentUserResolver.resolveUser();

        assertThat(resolved).isNotNull();
        assertThat(resolved.getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("should throw internal server error when authenticated user is missing from database")
    void should_throw_exception_when_user_not_found() {
        String email = "ghost@invoiceapp.com";

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> currentUserResolver.resolveUser())
                .isInstanceOf(InvoiceAppException.class)
                .hasMessageContaining("Authenticated user not found");
    }
}