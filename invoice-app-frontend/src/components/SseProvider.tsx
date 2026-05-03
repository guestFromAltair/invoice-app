import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { selectToken } from '../store/authSlice';
import { apiSlice } from '../store/apiSlice';
import { toast } from 'sonner';
import type { InvoiceNotification } from '@/types';

export function SseProvider() {
    const token = useSelector(selectToken);
    const dispatch = useDispatch();

    useEffect(() => {
        if (!token) return;

        const url = `/api/notifications/stream?token=${token}`;
        const eventSource = new EventSource(url);

        eventSource.addEventListener('invoice-update', (event) => {
            const notification: InvoiceNotification = JSON.parse(event.data);
            toast.info(notification.message, {
                description: `Invoice ${notification.invoiceNumber}`,
                duration: 5000
            });

            dispatch(
                apiSlice.util.invalidateTags([
                    { type: 'Invoice', id: notification.invoiceId },
                    { type: 'Invoice', id: 'LIST' },
                ])
            );
        });

        eventSource.onerror = () => {
            console.warn('SSE connection lost, reconnecting...');
        };

        return () => {
            eventSource.close();
        };
    }, [token, dispatch, toast]);

    return null;
}