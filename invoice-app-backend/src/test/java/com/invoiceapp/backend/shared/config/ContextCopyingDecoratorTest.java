package com.invoiceapp.backend.shared.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ContextCopyingDecorator Unit Tests")
class ContextCopyingDecoratorTest {

    @AfterEach
    void cleanUp() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("should copy and bind request attributes to background execution thread")
    void should_copy_request_attributes_to_background_thread() throws InterruptedException {
        ContextCopyingDecorator decorator = new ContextCopyingDecorator();
        RequestAttributes mockAttributes = mock(RequestAttributes.class);

        RequestContextHolder.setRequestAttributes(mockAttributes);

        AtomicReference<RequestAttributes> capturedAttributesInThread = new AtomicReference<>();
        AtomicBoolean taskExecuted = new AtomicBoolean(false);

        Runnable basicTask = () -> {
            capturedAttributesInThread.set(RequestContextHolder.getRequestAttributes());
            taskExecuted.set(true);
        };

        Runnable decoratedTask = decorator.decorate(basicTask);

        Thread workerThread = new Thread(decoratedTask);
        workerThread.start();
        workerThread.join();

        assertThat(taskExecuted.get()).isTrue();
        assertThat(capturedAttributesInThread.get()).isSameAs(mockAttributes);
    }

    @Test
    @DisplayName("should handle execution gracefully and clean up thread local variables when context is completely missing")
    void should_execute_gracefully_when_context_is_null() throws InterruptedException {
        ContextCopyingDecorator decorator = new ContextCopyingDecorator();
        RequestContextHolder.resetRequestAttributes();

        AtomicBoolean taskExecuted = new AtomicBoolean(false);
        Runnable basicTask = () -> taskExecuted.set(true);

        Runnable decoratedTask = decorator.decorate(basicTask);

        Thread workerThread = new Thread(decoratedTask);
        workerThread.start();
        workerThread.join();

        assertThat(taskExecuted.get()).isTrue();
        assertThat(RequestContextHolder.getRequestAttributes()).isNull();
    }
}