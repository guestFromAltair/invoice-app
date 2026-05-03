import {Routes, Route, Navigate} from 'react-router-dom';
import {useSelector} from 'react-redux';
import {selectIsAuthenticated} from './store/authSlice';
import {SseProvider} from './components/SseProvider';

import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import DashboardPage from './pages/DashboardPage';
import ClientsPage from './pages/ClientsPage';
import ClientDetailPage from './pages/ClientDetailPage';
import InvoicesPage from './pages/InvoicesPage';
import InvoiceDetailPage from './pages/InvoiceDetailPage';
import CreateInvoicePage from './pages/CreateInvoicePage';
import Layout from './components/Layout';
import React from "react";

function ProtectedRoute({children}: { children: React.ReactNode }) {
    const isAuthenticated = useSelector(selectIsAuthenticated);
    return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace/>;
}

export default function App() {
    const isAuthenticated = useSelector(selectIsAuthenticated);

    return (
        <>
            {isAuthenticated && <SseProvider/>}

            <Routes>
                <Route path="/login" element={<LoginPage/>}/>
                <Route path="/register" element={<RegisterPage/>}/>
                <Route
                    path="/"
                    element={
                        <ProtectedRoute>
                            <Layout/>
                        </ProtectedRoute>
                    }
                >
                    <Route index element={<Navigate to="/dashboard" replace/>}/>
                    <Route path="dashboard" element={<DashboardPage/>}/>
                    <Route path="clients" element={<ClientsPage/>}/>
                    <Route path="clients/:id" element={<ClientDetailPage/>}/>
                    <Route path="invoices" element={<InvoicesPage/>}/>
                    <Route path="invoices/new" element={<CreateInvoicePage/>}/>
                    <Route path="invoices/:id" element={<InvoiceDetailPage/>}/>
                </Route>
                <Route path="*" element={<Navigate to="/dashboard" replace/>}/>
            </Routes>
        </>
    );
}