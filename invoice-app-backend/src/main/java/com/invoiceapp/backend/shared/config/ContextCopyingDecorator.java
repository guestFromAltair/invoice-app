package com.invoiceapp.backend.shared.config;

import org.jspecify.annotations.NonNull;
import org.springframework.core.task.TaskDecorator;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public class ContextCopyingDecorator implements TaskDecorator {

    @Override
    public @NonNull Runnable decorate(@NonNull Runnable runnable) {
        RequestAttributes context = RequestContextHolder.getRequestAttributes();
        return () -> {
            try {
                if (context != null) {
                    // Bind the request thread's context to the new background worker thread
                    RequestContextHolder.setRequestAttributes(context);
                }
                runnable.run();
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        };
    }
}