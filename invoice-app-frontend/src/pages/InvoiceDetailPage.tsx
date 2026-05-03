import {useParams} from 'react-router-dom';
import {useSelector} from 'react-redux';
import {
    useGetInvoiceQuery,
    useGetPaymentsQuery,
    useSendInvoiceMutation,
    useCancelInvoiceMutation,
    useMarkInvoicePaidMutation,
    useRecordPaymentMutation,
} from '../store/apiSlice';
import {selectToken} from '../store/authSlice';
import {Button} from '../components/ui/button';
import {Badge} from '../components/ui/badge';
import {Card, CardContent, CardHeader, CardTitle} from '../components/ui/card';
import {Separator} from '../components/ui/separator';
import {toast} from 'sonner';
import {Download, Send, XCircle, CheckCircle} from 'lucide-react';
import {PaymentDialog} from '../components/PaymentDialog';
import type {InvoiceStatus, PaymentRequest} from '@/types';
import {skipToken} from '@reduxjs/toolkit/query';

const statusVariant: Record<InvoiceStatus, 'default' | 'secondary' | 'destructive' | 'outline'> = {
    DRAFT: 'secondary',
    SENT: 'default',
    PAID: 'outline',
    OVERDUE: 'destructive',
    CANCELLED: 'secondary'
};

export default function InvoiceDetailPage() {
    const {id} = useParams<{ id: string }>();
    const token = useSelector(selectToken);

    const {data: invoice, isLoading} = useGetInvoiceQuery(id ?? skipToken);
    const {data: payments = []} = useGetPaymentsQuery(id ?? skipToken);

    const [sendInvoice, {isLoading: isSending}] = useSendInvoiceMutation();
    const [cancelInvoice, {isLoading: isCancelling}] = useCancelInvoiceMutation();
    const [markPaid, {isLoading: isMarkingPaid}] = useMarkInvoicePaidMutation();
    const [recordPayment] = useRecordPaymentMutation();

    const handleTransition = async (
        action: () => Promise<unknown>,
        successMessage: string
    ) => {
        try {
            await action();
            toast.success(successMessage);
        } catch {
            toast.error('Action failed — please try again');
        }
    };

    const handleDownloadPdf = () => {
        window.open(
            `/api/invoices/${id}/pdf?token=${token}`,
            '_blank'
        );
    };

    const formatCurrency = (amount: number) =>
        new Intl.NumberFormat('fr-FR', {style: 'currency', currency: 'EUR'}).format(amount);

    if (isLoading) return (
        <div className="flex items-center justify-center h-64 text-muted-foreground">
            Loading invoice...
        </div>
    );

    if (!invoice) return (
        <div className="text-center py-12 text-muted-foreground">
            Invoice not found
        </div>
    );

    const canSend = invoice.status === 'DRAFT';
    const canCancel = invoice.status === 'DRAFT' || invoice.status === 'SENT' || invoice.status === 'OVERDUE';
    const canPay = invoice.status === 'SENT' || invoice.status === 'OVERDUE';

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold tracking-tight font-mono">
                        {invoice.invoiceNumber}
                    </h1>
                    <p className="text-muted-foreground mt-1">{invoice.clientName}</p>
                </div>
                <div className="flex items-center gap-2">
                    <Badge variant={statusVariant[invoice.status]} className="text-sm px-3 py-1">
                        {invoice.status}
                    </Badge>
                </div>
            </div>

            <div className="flex gap-2 flex-wrap">
                <Button variant="outline" onClick={handleDownloadPdf}>
                    <Download size={16} className="mr-2"/>
                    Download PDF
                </Button>

                {canSend && (
                    <Button
                        onClick={() => handleTransition(
                            () => sendInvoice(id!).unwrap(),
                            'Invoice sent'
                        )}
                        disabled={isSending}
                    >
                        <Send size={16} className="mr-2"/>
                        Send Invoice
                    </Button>
                )}

                {canPay && (
                    <PaymentDialog
                        invoiceId={id!}
                        remainingBalance={invoice.remainingBalance}
                        onSubmit={async (data: PaymentRequest) => {
                            await recordPayment({invoiceId: id!, body: data}).unwrap();
                            toast.success('Payment recorded');
                        }}
                    />
                )}

                {canCancel && (
                    <Button
                        variant="destructive"
                        onClick={() => handleTransition(
                            () => cancelInvoice(id!).unwrap(),
                            'Invoice cancelled'
                        )}
                        disabled={isCancelling}
                    >
                        <XCircle size={16} className="mr-2"/>
                        Cancel
                    </Button>
                )}

                {invoice.status === 'SENT' && (
                    <Button
                        variant="outline"
                        onClick={() => handleTransition(
                            () => markPaid(id!).unwrap(),
                            'Invoice marked as paid'
                        )}
                        disabled={isMarkingPaid}
                    >
                        <CheckCircle size={16} className="mr-2"/>
                        Mark as Paid
                    </Button>
                )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <Card>
                    <CardHeader><CardTitle>Details</CardTitle></CardHeader>
                    <CardContent className="space-y-3 text-sm">
                        {[
                            ['Issue date', invoice.issueDate],
                            ['Due date', invoice.dueDate],
                            ['Client', invoice.clientName],
                        ].map(([label, value]) => (
                            <div key={label} className="flex justify-between">
                                <span className="text-muted-foreground">{label}</span>
                                <span className="font-medium">{value}</span>
                            </div>
                        ))}
                        {invoice.notes && (
                            <>
                                <Separator/>
                                <p className="text-muted-foreground">{invoice.notes}</p>
                            </>
                        )}
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader><CardTitle>Financials</CardTitle></CardHeader>
                    <CardContent className="space-y-3 text-sm">
                        {[
                            ['Subtotal', formatCurrency(invoice.subtotal)],
                            [`Tax (${(invoice.taxRate * 100).toFixed(0)}%)`, formatCurrency(invoice.taxAmount)]
                        ].map(([label, value]) => (
                            <div key={label} className="flex justify-between">
                                <span className="text-muted-foreground">{label}</span>
                                <span>{value}</span>
                            </div>
                        ))}
                        <Separator/>
                        <div className="flex justify-between font-bold text-base">
                            <span>Total</span>
                            <span>{formatCurrency(invoice.total)}</span>
                        </div>
                        <div className="flex justify-between text-muted-foreground">
                            <span>Amount paid</span>
                            <span className="text-green-600">
                                {formatCurrency(invoice.amountPaid)}
                            </span>
                        </div>
                        <div className="flex justify-between font-semibold">
                            <span>Balance due</span>
                            <span className={invoice.remainingBalance > 0 ? 'text-destructive' : 'text-muted-foreground'}>
                                {formatCurrency(invoice.remainingBalance)}
                            </span>
                        </div>
                    </CardContent>
                </Card>
            </div>

            <Card>
                <CardHeader><CardTitle>Line Items</CardTitle></CardHeader>
                <CardContent>
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b text-muted-foreground">
                                <th className="text-left pb-2">Description</th>
                                <th className="text-right pb-2">Qty</th>
                                <th className="text-right pb-2">Unit price</th>
                                <th className="text-right pb-2">Discount</th>
                                <th className="text-right pb-2">Total</th>
                            </tr>
                        </thead>
                        <tbody>
                            {invoice.lineItems.map((item) => (
                                <tr key={item.id} className="border-b last:border-0">
                                    <td className="py-2">{item.description}</td>
                                    <td className="text-right py-2">{item.quantity}</td>
                                    <td className="text-right py-2">{formatCurrency(item.unitPrice)}</td>
                                    <td className="text-right py-2">
                                        {item.discountPct > 0 ? `${(item.discountPct * 100).toFixed(0)}%` : '—'}
                                    </td>
                                    <td className="text-right py-2 font-medium">{formatCurrency(item.lineTotal)}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </CardContent>
            </Card>

            {payments.length > 0 && (
                <Card>
                    <CardHeader><CardTitle>Payment History</CardTitle></CardHeader>
                    <CardContent className="space-y-2">
                        {payments.map((payment) => (
                            <div key={payment.id}
                                 className="flex justify-between items-center text-sm py-2 border-b last:border-0">
                                <div>
                                    <p className="font-medium">
                                        {formatCurrency(payment.amount)}
                                    </p>
                                    <p className="text-muted-foreground text-xs">
                                        {payment.method ?? 'Unknown method'} ·{' '}
                                        {new Date(payment.paidAt).toLocaleDateString('fr-FR')}
                                    </p>
                                </div>
                                {payment.notes && (
                                    <p className="text-muted-foreground text-xs max-w-xs text-right">
                                        {payment.notes}
                                    </p>
                                )}
                            </div>
                        ))}
                    </CardContent>
                </Card>
            )}
        </div>
    );
}