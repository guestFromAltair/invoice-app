import {createApi, fetchBaseQuery} from '@reduxjs/toolkit/query/react';
import type {RootState} from './index';
import type {
    AuthResponse, LoginRequest, RegisterRequest,
    Client, ClientRequest, Page,
    Invoice, InvoiceRequest, InvoiceStatus, LineItemRequest,
    Payment, PaymentRequest
} from '@/types';

export const apiSlice = createApi({
    reducerPath: 'api',
    baseQuery: fetchBaseQuery({
        baseUrl: '/api',
        prepareHeaders: (headers, {getState}) => {
            const token = (getState() as RootState).auth.token;
            if (token) {
                headers.set('Authorization', `Bearer ${token}`);
            }
            return headers;
        }
    }),

    tagTypes: ['Client', 'Invoice', 'Payment'],

    endpoints: (builder) => ({
        login: builder.mutation<AuthResponse, LoginRequest>({
            query: (credentials) => ({
                url: '/auth/login',
                method: 'POST',
                body: credentials
            })
        }),
        register: builder.mutation<AuthResponse, RegisterRequest>({
            query: (credentials) => ({
                url: '/auth/register',
                method: 'POST',
                body: credentials
            })
        }),
        getClients: builder.query<Page<Client>, { page?: number; size?: number }>({
            query: ({page = 0, size = 20} = {}) =>
                `/clients?page=${page}&size=${size}&sort=name,asc`,
            providesTags: (result) =>
                result
                    ? [
                        ...result.content.map(({id}) => ({
                            type: 'Client' as const, id
                        })),
                        {type: 'Client', id: 'LIST'}
                    ]
                    : [{type: 'Client', id: 'LIST'}]
        }),

        getClient: builder.query<Client, string>({
            query: (id) => `/clients/${id}`,
            providesTags: (_result, _error, id) => [{
                type: 'Client', id
            }]
        }),

        createClient: builder.mutation<Client, ClientRequest>({
            query: (body) => ({
                url: '/clients',
                method: 'POST',
                body
            }),
            invalidatesTags: [{type: 'Client', id: 'LIST'}]
        }),

        updateClient: builder.mutation<Client, { id: string; body: ClientRequest }>({
            query: ({id, body}) => ({
                url: `/clients/${id}`,
                method: 'PUT',
                body
            }),
            invalidatesTags: (_result, _error, {id}) => [
                {type: 'Client', id},
                {type: 'Client', id: 'LIST'}
            ]
        }),

        deleteClient: builder.mutation<void, string>({
            query: (id) => ({url: `/clients/${id}`, method: 'DELETE'}),
            invalidatesTags: [{type: 'Client', id: 'LIST'}]
        }),

        getInvoices: builder.query<Page<Invoice>, {
            page?: number;
            size?: number;
            status?: InvoiceStatus;
            clientId?: string
        }>({
            query: ({page = 0, size = 20, status, clientId} = {}) => {
                const params = new URLSearchParams({
                    page: String(page),
                    size: String(size),
                    sort: 'createdAt,desc'
                });
                if (status) params.append('status', status);
                if (clientId) params.append('clientId', clientId);
                return `/invoices?${params.toString()}`;
            },
            providesTags: (result) =>
                result
                    ? [
                        ...result.content.map(({id}) => ({
                            type: 'Invoice' as const, id
                        })),
                        {type: 'Invoice', id: 'LIST'}
                    ]
                    : [{type: 'Invoice', id: 'LIST'}]
        }),

        getInvoice: builder.query<Invoice, string>({
            query: (id) => `/invoices/${id}`,
            providesTags: (_result, _error, id) => [{
                type: 'Invoice', id
            }]
        }),

        createInvoice: builder.mutation<Invoice, InvoiceRequest>({
            query: (body) => ({
                url: '/invoices',
                method: 'POST',
                body
            }),
            invalidatesTags: [{type: 'Invoice', id: 'LIST'}]
        }),

        updateLineItems: builder.mutation<Invoice, { id: string; lineItems: LineItemRequest[] }>({
            query: ({id, lineItems}) => ({
                url: `/invoices/${id}/line-items`,
                method: 'PUT',
                body: lineItems
            }),
            invalidatesTags: (_result, _error, {id}) => [{
                type: 'Invoice', id}
            ]
        }),

        sendInvoice: builder.mutation<Invoice, string>({
            query: (id) => ({url: `/invoices/${id}/send`, method: 'POST'}),
            invalidatesTags: (_result, _error, id) => [
                {type: 'Invoice', id},
                {type: 'Invoice', id: 'LIST'}
            ]
        }),

        cancelInvoice: builder.mutation<Invoice, string>({
            query: (id) => ({url: `/invoices/${id}/cancel`, method: 'POST'}),
            invalidatesTags: (_result, _error, id) => [
                {type: 'Invoice', id},
                {type: 'Invoice', id: 'LIST'}
            ]
        }),

        markInvoicePaid: builder.mutation<Invoice, string>({
            query: (id) => ({url: `/invoices/${id}/mark-paid`, method: 'POST'}),
            invalidatesTags: (_result, _error, id) => [
                {type: 'Invoice', id},
                {type: 'Invoice', id: 'LIST'}
            ]
        }),

        getPayments: builder.query<Payment[], string>({
            query: (invoiceId) => `/invoices/${invoiceId}/payments`,
            providesTags: (_result, _error, invoiceId) => [
                {type: 'Payment', id: invoiceId}
            ]
        }),

        recordPayment: builder.mutation<Payment, { invoiceId: string; body: PaymentRequest }>({
            query: ({invoiceId, body}) => ({
                url: `/invoices/${invoiceId}/payments`,
                method: 'POST',
                body
            }),
            invalidatesTags: (_result, _error, {invoiceId}) => [
                {type: 'Payment', id: invoiceId},
                {type: 'Invoice', id: invoiceId},
                {type: 'Invoice', id: 'LIST'}
            ]
        })
    })
});

export const {
    useLoginMutation,
    useRegisterMutation,
    useGetClientsQuery,
    useGetClientQuery,
    useCreateClientMutation,
    useUpdateClientMutation,
    useDeleteClientMutation,
    useGetInvoicesQuery,
    useGetInvoiceQuery,
    useCreateInvoiceMutation,
    useUpdateLineItemsMutation,
    useSendInvoiceMutation,
    useCancelInvoiceMutation,
    useMarkInvoicePaidMutation,
    useGetPaymentsQuery,
    useRecordPaymentMutation
} = apiSlice;