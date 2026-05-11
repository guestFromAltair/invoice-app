import React from "react";
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';
import { configureStore } from '@reduxjs/toolkit';
import { authReducer } from '@/store/authSlice.ts';
import { apiSlice } from '@/store/apiSlice.ts';
import LoginPage from '../../pages/LoginPage';
import { toast } from 'sonner';

vi.mock('sonner', () => ({
    toast: {
        error: vi.fn(),
        success: vi.fn(),
    },
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
    const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
    return {
        ...actual,
        useNavigate: () => mockNavigate,
    };
});

const mockLoginTrigger = vi.fn();

vi.mock('@/store/apiSlice.ts', async () => {
    const actual = await vi.importActual<typeof import('@/store/apiSlice.ts')>('@/store/apiSlice.ts');
    return {
        ...actual,
        useLoginMutation: () => [
            mockLoginTrigger,
            { isLoading: false }
        ]
    };
});

function renderWithProviders(ui: React.ReactElement) {
    const store = configureStore({
        reducer: {
            auth: authReducer,
            [apiSlice.reducerPath]: apiSlice.reducer
        },
        middleware: (getDefaultMiddleware) =>
            getDefaultMiddleware().concat(apiSlice.middleware)
    });

    return render(
        <Provider store={store}>
            <BrowserRouter>
                {ui}
            </BrowserRouter>
        </Provider>
    );
}

describe('LoginPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('should render email and password fields', () => {
        renderWithProviders(<LoginPage />);

        expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
        expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
    });

    it('should show validation errors for empty submission', async () => {
        renderWithProviders(<LoginPage />);
        const user = userEvent.setup();

        await user.click(screen.getByRole('button', { name: /sign in/i }));

        await waitFor(() => {
            expect(screen.getByText(/enter a valid email/i)).toBeInTheDocument();
        });
    });

    it('should show validation error for invalid email format', async () => {
        renderWithProviders(<LoginPage />);
        const user = userEvent.setup();

        await user.type(screen.getByLabelText(/email/i), 'not-an-@email');
        await user.click(screen.getByRole('button', { name: /sign in/i }));

        await waitFor(() => {
            expect(screen.getByText(/valid email/i)).toBeInTheDocument();
        });
    });

    it('should show validation error for short password', async () => {
        renderWithProviders(<LoginPage />);
        const user = userEvent.setup();

        await user.type(screen.getByLabelText(/email/i), 'test@example.com');
        await user.type(screen.getByLabelText(/password/i), 'short');
        await user.click(screen.getByRole('button', { name: /sign in/i }));

        await waitFor(() => {
            expect(screen.getByText(/at least 8 characters/i)).toBeInTheDocument();
        });
    });

    it('should successfully login, dispatch credentials, and navigate to dashboard', async () => {
        const mockResponseData = { user: { email: 'test@example.com' }, token: 'mock-jwt-token' };

        mockLoginTrigger.mockReturnValue({
            unwrap: async () => mockResponseData
        });

        renderWithProviders(<LoginPage />);
        const user = userEvent.setup();

        await user.type(screen.getByLabelText(/email/i), 'test@example.com');
        await user.type(screen.getByLabelText(/password/i), 'password123');
        await user.click(screen.getByRole('button', { name: /sign in/i }));

        await waitFor(() => {
            expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
        });
    });

    it('should show an error toast when login API request fails', async () => {
        mockLoginTrigger.mockReturnValue({
            unwrap: async () => {
                throw new Error('Unauthorized');
            }
        });

        renderWithProviders(<LoginPage />);
        const user = userEvent.setup();

        await user.type(screen.getByLabelText(/email/i), 'test@example.com');
        await user.type(screen.getByLabelText(/password/i), 'wrongpassword');
        await user.click(screen.getByRole('button', { name: /sign in/i }));

        await waitFor(() => {
            expect(toast.error).toHaveBeenCalledWith('Invalid email or password');
        });
    });

    it('should navigate to the register page when clicking the register link', async () => {
        renderWithProviders(<LoginPage />);

        const registerLink = screen.getByRole('link', { name: /register/i });
        expect(registerLink).toBeInTheDocument();
        expect(registerLink.getAttribute('href')).toBe('/register');
    });
});