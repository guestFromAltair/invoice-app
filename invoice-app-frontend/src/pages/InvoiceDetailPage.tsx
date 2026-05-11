import {useNavigate, useParams} from 'react-router-dom';
import {
    useGetInvoiceQuery,
    useGetPaymentsQuery,
    useSendInvoiceMutation,
    useCancelInvoiceMutation,
    useMarkInvoicePaidMutation,
    useRecordPaymentMutation, useLazyDownloadInvoicePdfQuery,
} from '../store/apiSlice';
import {Button} from '../components/ui/button';
import {Badge} from '../components/ui/badge';
import {Card, CardContent, CardHeader, CardTitle} from '../components/ui/card';
import {Separator} from '../components/ui/separator';
import {toast} from 'sonner';
import {Download, Send, XCircle, CheckCircle, ArrowLeft} from 'lucide-react';
import {PaymentDialog} from '../components/PaymentDialog';
import type {PaymentRequest} from '@/types';
import {skipToken} from '@reduxjs/toolkit/query';
import {statusVariant} from "@/utils/invoiceStatus.ts";

export default function InvoiceDetailPage() {
    const navigate = useNavigate();
    const { id: rawId } = useParams<{ id: string }>();

    const { data: invoice, isLoading } = useGetInvoiceQuery(rawId ?? skipToken);
    const { data: payments = [] } = useGetPaymentsQuery(rawId ?? skipToken);

    const [sendInvoice, {isLoading: isSending}] = useSendInvoiceMutation();
    const [cancelInvoice, {isLoading: isCancelling}] = useCancelInvoiceMutation();
    const [markPaid, {isLoading: isMarkingPaid}] = useMarkInvoicePaidMutation();
    const [recordPayment] = useRecordPaymentMutation();
    const [triggerDownload, { isFetching: isDownloading }] = useLazyDownloadInvoicePdfQuery();

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64 text-muted-foreground">
                Loading invoice...
            </div>
        );
    }

    if (!invoice || !rawId) {
        return (
            <div className="text-center py-12 text-muted-foreground">
                Invoice not found
            </div>
        );
    }

    const id = rawId;

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

    const handleDownloadPdf = async () => {
        try {
            const blob = await triggerDownload(id).unwrap();
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');

            link.href = url;
            link.download = `${invoice.invoiceNumber}.pdf`;
            document.body.appendChild(link);
            link.click();

            document.body.removeChild(link);
            URL.revokeObjectURL(url);
            toast.success('PDF downloaded');
        } catch {
            toast.error('Failed to download PDF');
        }
    };

    const formatCurrency = (amount: number) =>
        new Intl.NumberFormat('fr-FR', {style: 'currency', currency: 'EUR'}).format(amount);

    const canSend = invoice.status === 'DRAFT';
    const canCancel = invoice.status === 'DRAFT' || invoice.status === 'SENT' || invoice.status === 'OVERDUE';
    const canPay = invoice.status === 'SENT' || invoice.status === 'OVERDUE';

    return (
        <div className="space-y-6 max-w-full overflow-hidden p-0.5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-start sm:items-center gap-3 sm:gap-4">
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => navigate('/invoices')}
                        className="shrink-0 mt-1 sm:mt-0"
                    >
                        <ArrowLeft size={18}/>
                    </Button>
                    <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight font-mono break-all">
                                {invoice.invoiceNumber}
                            </h1>
                            <Badge variant={statusVariant[invoice.status]} className="text-xs px-2.5 py-0.5 ml-1">
                                {invoice.status}
                            </Badge>
                        </div>
                        <p className="text-muted-foreground mt-1 text-sm sm:text-base truncate">
                            {invoice.clientName}
                        </p>
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-2 sm:flex sm:flex-row gap-2 w-full">
                <Button
                    variant="outline"
                    onClick={handleDownloadPdf}
                    disabled={isDownloading}
                    className="w-full sm:w-auto h-10 text-xs sm:text-sm px-3"
                >
                    <Download size={15} className="mr-2 shrink-0" />
                    <span className="truncate">{isDownloading ? 'Generating...' : 'PDF'}</span>
                </Button>

                {canSend && (
                    <Button
                        onClick={() => handleTransition(
                            () => sendInvoice(id).unwrap(),
                            'Invoice sent'
                        )}
                        disabled={isSending}
                        className="w-full sm:w-auto h-10 text-xs sm:text-sm px-3"
                    >
                        <Send size={15} className="mr-2 shrink-0"/>
                        <span className="truncate">Send Invoice</span>
                    </Button>
                )}

                {canPay && (
                    <div className="col-span-1 sm:contents">
                        <PaymentDialog
                            invoiceId={id}
                            remainingBalance={invoice.remainingBalance}
                            onSubmit={async (data: PaymentRequest) => {
                                await recordPayment({invoiceId: id, body: data}).unwrap();
                                toast.success('Payment recorded');
                            }}
                        />
                    </div>
                )}

                {invoice.status === 'SENT' && (
                    <Button
                        variant="outline"
                        onClick={() => handleTransition(
                            () => markPaid(id).unwrap(),
                            'Invoice marked as paid'
                        )}
                        disabled={isMarkingPaid}
                        className="w-full sm:w-auto h-10 text-xs sm:text-sm px-3"
                    >
                        <CheckCircle size={15} className="mr-2 shrink-0"/>
                        <span className="truncate">Mark Paid</span>
                    </Button>
                )}

                {canCancel && (
                    <Button
                        variant="destructive"
                        onClick={() => handleTransition(
                            () => cancelInvoice(id).unwrap(),
                            'Invoice cancelled'
                        )}
                        disabled={isCancelling}
                        className="col-span-2 sm:w-auto h-10 text-xs sm:text-sm px-3"
                    >
                        <XCircle size={15} className="mr-2 shrink-0"/>
                        <span className="truncate">Cancel Invoice</span>
                    </Button>
                )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <Card>
                    <CardHeader className="pb-3"><CardTitle className="text-base sm:text-lg">Details</CardTitle></CardHeader>
                    <CardContent className="space-y-3 text-sm">
                        {[
                            ['Issue date', invoice.issueDate],
                            ['Due date', invoice.dueDate],
                            ['Client', invoice.clientName],
                        ].map(([label, value]) => (
                            <div key={label} className="flex justify-between gap-4">
                                <span className="text-muted-foreground shrink-0">{label}</span>
                                <span className="font-medium text-right break-all">{value}</span>
                            </div>
                        ))}
                        {invoice.notes && (
                            <>
                                <Separator />
                                <div className="space-y-1">
                                    <span className="text-xs text-muted-foreground font-semibold">Notes</span>
                                    <p className="text-muted-foreground leading-relaxed wrap-break-word">{invoice.notes}</p>
                                </div>
                            </>
                        )}
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader className="pb-3"><CardTitle className="text-base sm:text-lg">Financials</CardTitle></CardHeader>
                    <CardContent className="space-y-3 text-sm">
                        {[
                            ['Subtotal', formatCurrency(invoice.subtotal)],
                            [`Tax (${(invoice.taxRate * 100).toFixed(0)}%)`, formatCurrency(invoice.taxAmount)]
                        ].map(([label, value]) => (
                            <div key={label} className="flex justify-between gap-4">
                                <span className="text-muted-foreground shrink-0">{label}</span>
                                <span className="text-right">{value}</span>
                            </div>
                        ))}
                        <Separator />
                        <div className="flex justify-between font-bold text-base gap-4">
                            <span>Total</span>
                            <span className="text-right">{formatCurrency(invoice.total)}</span>
                        </div>
                        <div className="flex justify-between text-muted-foreground gap-4">
                            <span>Amount paid</span>
                            <span className="text-green-600 text-right font-medium">
                                {formatCurrency(invoice.amountPaid)}
                            </span>
                        </div>
                        <div className="flex justify-between font-semibold gap-4">
                            <span>Balance due</span>
                            <span className={`text-right ${invoice.remainingBalance > 0 ? 'text-destructive font-bold' : 'text-muted-foreground'}`}>
                                {formatCurrency(invoice.remainingBalance)}
                            </span>
                        </div>
                    </CardContent>
                </Card>
            </div>

            <Card>
                <CardHeader className="pb-3"><CardTitle className="text-base sm:text-lg">Line Items</CardTitle></CardHeader>
                <CardContent className="px-3 sm:px-6">
                    <div className="block md:hidden space-y-4">
                        {invoice.lineItems.map((item) => (
                            <div key={item.id} className="p-3.5 border rounded-lg bg-muted/10 space-y-2">
                                <div className="font-semibold text-sm text-foreground wrap-break-word">
                                    {item.description}
                                </div>
                                <div className="grid grid-cols-3 gap-2 pt-1.5 border-t text-xs text-muted-foreground">
                                    <div>
                                        <p className="font-medium text-[10px] uppercase tracking-wider">Qty</p>
                                        <p className="text-foreground font-medium mt-0.5">{item.quantity}</p>
                                    </div>
                                    <div>
                                        <p className="font-medium text-[10px] uppercase tracking-wider">Unit Price</p>
                                        <p className="text-foreground font-medium mt-0.5">{formatCurrency(item.unitPrice)}</p>
                                    </div>
                                    <div>
                                        <p className="font-medium text-[10px] uppercase tracking-wider">Discount</p>
                                        <p className="text-foreground font-medium mt-0.5">
                                            {item.discountPct > 0 ? `${(item.discountPct * 100).toFixed(0)}%` : '—'}
                                        </p>
                                    </div>
                                </div>
                                <div className="flex justify-between items-center pt-2 border-t mt-1">
                                    <span className="text-xs font-semibold text-muted-foreground">Item Total</span>
                                    <span className="text-sm font-bold text-primary">{formatCurrency(item.lineTotal)}</span>
                                </div>
                            </div>
                        ))}
                    </div>

                    <div className="hidden md:block overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b text-muted-foreground">
                                    <th className="text-left pb-2 font-semibold">Description</th>
                                    <th className="text-right pb-2 font-semibold w-16">Qty</th>
                                    <th className="text-right pb-2 font-semibold w-32">Unit price</th>
                                    <th className="text-right pb-2 font-semibold w-24">Discount</th>
                                    <th className="text-right pb-2 font-semibold w-32">Total</th>
                                </tr>
                            </thead>
                            <tbody>
                                {invoice.lineItems.map((item) => (
                                    <tr key={item.id} className="border-b last:border-0 hover:bg-muted/5">
                                        <td className="py-3 pr-4 max-w-xs truncate">{item.description}</td>
                                        <td className="text-right py-3">{item.quantity}</td>
                                        <td className="text-right py-3">{formatCurrency(item.unitPrice)}</td>
                                        <td className="text-right py-3">
                                            {item.discountPct > 0 ? `${(item.discountPct * 100).toFixed(0)}%` : '—'}
                                        </td>
                                        <td className="text-right py-3 font-semibold">{formatCurrency(item.lineTotal)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </CardContent>
            </Card>

            {payments.length > 0 && (
                <Card>
                    <CardHeader className="pb-3"><CardTitle className="text-base sm:text-lg">Payment History</CardTitle></CardHeader>
                    <CardContent className="space-y-1">
                        {payments.map((payment) => (
                            <div key={payment.id}
                                 className="flex justify-between items-center text-sm py-3 border-b last:border-0 hover:bg-muted/5 rounded-md px-1">
                                <div className="min-w-0 pr-4">
                                    <p className="font-bold text-foreground">
                                        {formatCurrency(payment.amount)}
                                    </p>
                                    <p className="text-muted-foreground text-xs mt-0.5 truncate">
                                        {payment.method ?? 'Unknown method'} ·{' '}
                                        {new Date(payment.paidAt).toLocaleDateString('fr-FR')}
                                    </p>
                                </div>
                                {payment.notes && (
                                    <p className="text-muted-foreground text-xs max-w-[50%] text-right wrap-break-word line-clamp-2">
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