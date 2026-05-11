import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
    authReducer,
    setCredentials,
    logout,
    selectToken,
    selectCurrentUser,
    selectIsAuthenticated,
    type AuthState
} from '@/store/authSlice';
import type { RootState } from '@/store';
import type { AuthResponse } from '@/types';

describe('authSlice', () => {
    beforeEach(() => {
        vi.restoreAllMocks();
        localStorage.clear();
    });

    describe('Reducer initial state', () => {
        it('should load initial state from localStorage if values exist', () => {
            localStorage.setItem('token', 'saved-token');
            localStorage.setItem('email', 'saved@example.com');
            localStorage.setItem('role', 'ADMIN');

            const stateFromStorage: AuthState = {
                token: localStorage.getItem('token'),
                email: localStorage.getItem('email'),
                role: localStorage.getItem('role') as AuthState['role']
            };

            expect(stateFromStorage).toEqual({
                token: 'saved-token',
                email: 'saved@example.com',
                role: 'ADMIN'
            });
        });

        it('should handle default empty initial state', () => {
            const state = authReducer(undefined, { type: 'unknown' });
            expect(state).toEqual({
                token: null,
                email: null,
                role: null
            });
        });
    });

    describe('Reducers', () => {
        it('should handle setCredentials', () => {
            const initialState: AuthState = {
                token: null,
                email: null,
                role: null
            };

            const payload: AuthResponse = {
                token: 'jwt-token-123',
                email: 'user@example.com',
                role: 'USER'
            };

            const nextState = authReducer(initialState, setCredentials(payload));
            expect(nextState).toEqual({
                token: 'jwt-token-123',
                email: 'user@example.com',
                role: 'USER'
            });

            expect(localStorage.getItem('token')).toBe('jwt-token-123');
            expect(localStorage.getItem('email')).toBe('user@example.com');
            expect(localStorage.getItem('role')).toBe('USER');
        });

        it('should handle logout', () => {
            localStorage.setItem('token', 'active-token');
            localStorage.setItem('email', 'active@example.com');
            localStorage.setItem('role', 'ADMIN');

            const populatedState: AuthState = {
                token: 'active-token',
                email: 'active@example.com',
                role: 'ADMIN'
            };

            const nextState = authReducer(populatedState, logout());
            expect(nextState).toEqual({
                token: null,
                email: null,
                role: null
            });

            expect(localStorage.getItem('token')).toBeNull();
            expect(localStorage.getItem('email')).toBeNull();
            expect(localStorage.getItem('role')).toBeNull();
        });
    });

    describe('Selectors', () => {
        const mockRootState = {
            auth: {
                token: 'selector-token',
                email: 'selector@example.com',
                role: 'ADMIN' as const
            }
        } as unknown as RootState;

        it('should selectToken', () => {
            expect(selectToken(mockRootState)).toBe('selector-token');
        });

        it('should selectCurrentUser', () => {
            expect(selectCurrentUser(mockRootState)).toEqual({
                email: 'selector@example.com',
                role: 'ADMIN'
            });
        });

        it('should selectIsAuthenticated when token exists', () => {
            expect(selectIsAuthenticated(mockRootState)).toBe(true);
        });

        it('should selectIsAuthenticated when token is null', () => {
            const unauthenticatedState = {
                auth: {
                    token: null,
                    email: null,
                    role: null
                }
            } as unknown as RootState;

            expect(selectIsAuthenticated(unauthenticatedState)).toBe(false);
        });
    });
});