// Auth
export interface AuthResponse {
    token: string;
    email: string;
    role: 'USER' | 'ADMIN';
}

export interface LoginRequest {
    email: string;
    password: string;
}

export interface RegisterRequest {
    email: string;
    password: string;
}

// Pagination
export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
    first: boolean;
    last: boolean;
}

// Client
export interface Client {
    id: string;
    name: string;
    email: string | null;
    phone: string | null;
    address: string | null;
    vatNumber: string | null;
    createdAt: string;
}

export interface ClientRequest {
    name: string;
    email?: string;
    phone?: string;
    address?: string;
    vatNumber?: string;
}

// Invoice
export type InvoiceStatus =
    | 'DRAFT'
    | 'SENT'
    | 'PAID'
    | 'OVERDUE'
    | 'CANCELLED';

export interface LineItem {
    id: string;
    description: string;
    quantity: number;
    unitPrice: number;
    discountPct: number;
    lineTotal: number;
    position: number;
}

export interface Invoice {
    id: string;
    invoiceNumber: string;
    clientName: string;
    clientId: string;
    status: InvoiceStatus;
    issueDate: string;
    dueDate: string;
    subtotal: number;
    taxRate: number;
    taxAmount: number;
    total: number;
    amountPaid: number;
    remainingBalance: number;
    notes: string | null;
    lineItems: LineItem[];
    createdAt: string;
}

export interface LineItemRequest {
    description: string;
    quantity: number;
    unitPrice: number;
    discountPct: number;
    position: number;
}

export interface InvoiceRequest {
    clientId: string;
    issueDate: string;
    dueDate: string;
    taxRate: number;
    notes?: string;
    lineItems: LineItemRequest[];
}

// Payment
export interface Payment {
    id: string;
    amount: number;
    paidAt: string;
    method: string | null;
    notes: string | null;
    createdAt: string;
}

export interface PaymentRequest {
    amount: number;
    paidAt?: string;
    method?: string;
    notes?: string;
}

// SSE Notification
export interface InvoiceNotification {
    type: 'STATUS_CHANGED' | 'OVERDUE_DETECTED';
    invoiceId: string;
    invoiceNumber: string;
    newStatus: InvoiceStatus;
    message: string;
}