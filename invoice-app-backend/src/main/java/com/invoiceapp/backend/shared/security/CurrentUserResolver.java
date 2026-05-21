package com.invoiceapp.backend.shared.security;

import com.invoiceapp.backend.auth.domain.User;
import com.invoiceapp.backend.auth.domain.UserRepository;
import com.invoiceapp.backend.shared.exception.InvoiceAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public User resolveUser() {
        String email = Objects.requireNonNull(
                SecurityContextHolder.getContext().getAuthentication(),
                "No authentication found in SecurityContext"
        ).getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvoiceAppException(
                        "Authenticated user not found",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));
    }
}