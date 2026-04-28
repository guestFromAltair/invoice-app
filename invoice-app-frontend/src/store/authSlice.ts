import { createSlice, type PayloadAction } from '@reduxjs/toolkit';
import type { RootState } from './index';
import type { AuthResponse } from '@/types';

export interface AuthState {
    token: string | null;
    email: string | null;
    role: 'USER' | 'ADMIN' | null;
}

const initialState: AuthState = {
    token: localStorage.getItem('token'),
    email: localStorage.getItem('email'),
    role: localStorage.getItem('role') as AuthState['role']
};

const authSlice = createSlice({
    name: 'auth',
    initialState,
    reducers: {
        setCredentials: (state, action: PayloadAction<AuthResponse>) => {
            state.token = action.payload.token;
            state.email = action.payload.email;
            state.role = action.payload.role;
            localStorage.setItem('token', action.payload.token);
            localStorage.setItem('email', action.payload.email);
            localStorage.setItem('role', action.payload.role);
        },
        logout: (state) => {
            state.token = null;
            state.email = null;
            state.role = null;
            localStorage.removeItem('token');
            localStorage.removeItem('email');
            localStorage.removeItem('role');
        }
    }
});

export const { setCredentials, logout } = authSlice.actions;
export const authReducer = authSlice.reducer;

export const selectToken = (state: RootState) => state.auth.token;
export const selectCurrentUser = (state: RootState) => ({
    email: state.auth.email,
    role: state.auth.role
});
export const selectIsAuthenticated = (state: RootState) =>
    state.auth.token !== null;