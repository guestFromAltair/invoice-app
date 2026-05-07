import { useEffect, useRef } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { selectToken } from '../store/authSlice';
import { apiSlice } from '../store/apiSlice';
import { toast } from 'sonner';
import type { InvoiceNotification } from '@/types';
import { type EventSourceMessage, fetchEventSource } from "@microsoft/fetch-event-source";

export function SseProvider() {
    const token = useSelector(selectToken);
    const dispatch = useDispatch();

    const abortControllerRef = useRef<AbortController | null>(null);

    useEffect(() => {
        if (!token) return;

        const connect = () => {
            if (abortControllerRef.current) {
                abortControllerRef.current.abort();
            }

            const controller = new AbortController();
            abortControllerRef.current = controller;

            fetchEventSource('/api/notifications/stream', {
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
                signal: controller.signal,
                openWhenHidden: false,

                onmessage(event: EventSourceMessage) {
                    if (event.event !== 'invoice-update') return;

                    try {
                        const notification: InvoiceNotification = JSON.parse(event.data);

                        toast.info(notification.message, {
                            description: `Invoice ${notification.invoiceNumber}`,
                            duration: 5000
                        });

                        setTimeout(() => {
                            dispatch(
                                apiSlice.util.invalidateTags([
                                    { type: 'Invoice', id: notification.invoiceId },
                                    { type: 'Invoice', id: 'LIST' }
                                ])
                            );
                        }, 300);
                    } catch {
                        console.warn('Failed to parse SSE notification', event.data);
                    }
                },
                async onopen(response) {
                    if (response.ok) {
                        console.log("SSE Connection established");
                        return;
                    }

                    if (response.status >= 400) {
                        console.error("SSE Server Error:", response.status);
                        throw new Error(`Server returned ${response.status}`);
                    }
                },
                onclose() {
                    console.log("Server closed the connection. Reconnecting...");
                    throw new Error("Server closed connection");
                },
                onerror(err) {
                    console.warn('SSE Error. Retrying in 5s...', err);
                    return 5000;
                },
            });
        };

        const handleVisibility = () => {
            if (document.visibilityState === 'visible') {
                connect();
            } else {
                abortControllerRef.current?.abort();
            }
        };

        connect();

        document.addEventListener('visibilitychange', handleVisibility);

        return () => {
            document.removeEventListener('visibilitychange', handleVisibility);
            abortControllerRef.current?.abort();
        };
    }, [token, dispatch]);

    return null;
}