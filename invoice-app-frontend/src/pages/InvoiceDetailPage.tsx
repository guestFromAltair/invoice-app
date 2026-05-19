import {useState} from 'react';
import {useParams, useNavigate} from 'react-router-dom';
import {useForm, useFieldArray} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {Controller} from 'react-hook-form';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue
} from '../components/ui/select';
import {z} from 'zod';
import {
    useGetInvoiceQuery,
    useGetPaymentsQuery,
    useSendInvoiceMutation,
    useCancelInvoiceMutation,
    useMarkInvoicePaidMutation,
    useRecordPaymentMutation,
    useUpdateInvoiceMutation,
    useLazyDownloadInvoicePdfQuery,
    apiSlice,
} from '../store/apiSlice';
import {Button} from '../components/ui/button';
import {Badge} from '../components/ui/badge';
import {Card, CardContent, CardHeader, CardTitle} from '../components/ui/card';
import {Separator} from '../components/ui/separator';
import {Input} from '../components/ui/input';
import {Textarea} from '../components/ui/textarea';
import {toast} from 'sonner';
import {Download, Send, XCircle, CheckCircle, ArrowLeft, Pencil, Check, X, Plus, Trash2} from 'lucide-react';
import {PaymentDialog} from '../components/PaymentDialog';
import type {PaymentRequest, UpdateInvoiceRequest} from '@/types';
import {skipToken} from '@reduxjs/toolkit/query';
import {statusVariant} from "@/utils/invoiceStatus.ts";
import {useDispatch} from "react-redux";

const invoiceUpdateSchema = z.object({
    issueDate: z.string().min(1, 'Issue date is required'),
    dueDate: z.string().min(1, 'Due date is required'),
    taxRate: z.number().min(0).max(1),
    notes: z.string().max(1000).optional(),
    lineItems: z.array(
        z.object({
            description: z.string().min(1, 'Description is required').max(255),
            quantity: z.number().min(1, 'Min quantity is 1'),
            unitPrice: z.number().min(0, 'Min unit price is 0'),
            discountPct: z.number().min(0).max(1),
            position: z.number()
        })
    ).min(1, 'An invoice requires at least one line item')
});

type InvoiceUpdateFormData = z.infer<typeof invoiceUpdateSchema>;

const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('fr-FR', {style: 'currency', currency: 'EUR'}).format(amount);

export default function InvoiceDetailPage() {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const {id} = useParams<{ id: string }>();
    const [isEditing, setIsEditing] = useState(false);

    const {data: invoice, isLoading} = useGetInvoiceQuery(id ?? skipToken);
    const {data: payments = []} = useGetPaymentsQuery(id ?? skipToken);

    const [sendInvoice, {isLoading: isSending}] = useSendInvoiceMutation();
    const [cancelInvoice, {isLoading: isCancelling}] = useCancelInvoiceMutation();
    const [markPaid, {isLoading: isMarkingPaid}] = useMarkInvoicePaidMutation();
    const [updateInvoice, {isLoading: isUpdating}] = useUpdateInvoiceMutation();
    const [recordPayment] = useRecordPaymentMutation();
    const [triggerDownload, {isFetching: isDownloading}] = useLazyDownloadInvoicePdfQuery();

    const {register, control, handleSubmit, reset, formState: {errors}} = useForm<InvoiceUpdateFormData>({
        resolver: zodResolver(invoiceUpdateSchema),
        values: invoice
            ? {
                issueDate: invoice.issueDate,
                dueDate: invoice.dueDate,
                taxRate: invoice.taxRate,
                notes: invoice.notes || '',
                lineItems: invoice.lineItems.map((item, index) => ({
                    description: item.description,
                    quantity: item.quantity,
                    unitPrice: item.unitPrice,
                    discountPct: item.discountPct,
                    position: item.position ?? index
                }))
            }
            : undefined
    });

    const {fields, append, remove} = useFieldArray({
        control,
        name: 'lineItems'
    });

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64 text-muted-foreground">
                Loading invoice...
            </div>
        );
    }

    if (!invoice || !id) {
        return (
            <div className="text-center py-12 text-muted-foreground">
                Invoice not found
            </div>
        );
    }

    const onSubmit = async (formData: InvoiceUpdateFormData) => {
        try {
            const updatePayload: UpdateInvoiceRequest = {
                version: invoice.version,
                issueDate: formData.issueDate,
                dueDate: formData.dueDate,
                taxRate: formData.taxRate,
                notes: formData.notes,
                lineItems: formData.lineItems
            };

            await updateInvoice({
                id,
                body: updatePayload
            }).unwrap();

            toast.success('Invoice updated successfully');
            setIsEditing(false);
        } catch (error: unknown) {
            const rtkErrorData = (error as { data?: { type?: string } })?.data;
            if (rtkErrorData?.type === 'OPTIMISTIC_LOCK_FAILURE') {
                toast.error('This invoice was modified in another session. Refreshing details...');
                dispatch(apiSlice.util.invalidateTags([{type: 'Invoice', id}]));
                setIsEditing(false);
            } else {
                toast.error('Failed to save changes');
            }
        }
    };

    const handleCancelEdit = () => {
        reset();
        setIsEditing(false);
    };

    const handleTransition = async (action: () => Promise<unknown>, successMessage: string) => {
        try {
            await action();
            toast.success(successMessage);
        } catch (error: unknown) {
            const rtkErrorData = (error as { data?: { type?: string } })?.data;
            if (rtkErrorData?.type === 'OPTIMISTIC_LOCK_FAILURE') {
                toast.error('Invoice records are out of sync. Reloading fresh state...');
                dispatch(apiSlice.util.invalidateTags([{type: 'Invoice', id}]));
            } else {
                toast.error('Action failed — please try again');
            }
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

    const canSend = invoice.status === 'DRAFT';
    const canCancel = invoice.status === 'DRAFT' || invoice.status === 'SENT' || invoice.status === 'OVERDUE';
    const canPay = invoice.status === 'SENT' || invoice.status === 'OVERDUE';

    return (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-6 max-w-full overflow-hidden p-0.5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-start gap-3">
                    <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() => navigate('/invoices')}
                        className="h-9 w-9 shrink-0 mt-0.5"
                    >
                        <ArrowLeft size={18}/>
                    </Button>
                    <div className="min-w-0">
                        <div className="flex flex-wrap items-center gap-2">
                            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight font-mono break-all text-foreground">
                                {invoice.invoiceNumber}
                            </h1>
                            <Badge variant={statusVariant[invoice.status]} className="text-xs px-2.5 py-0.5">
                                {invoice.status}
                            </Badge>
                        </div>
                        <p className="text-muted-foreground mt-1 text-xs sm:text-sm truncate">
                            {invoice.clientName}
                        </p>
                    </div>
                </div>

                {invoice.status === 'DRAFT' && !isEditing && (
                    <Button
                        type="button"
                        variant="outline"
                        onClick={() => setIsEditing(true)}
                        className="w-full sm:w-auto h-9 text-xs sm:text-sm"
                    >
                        <Pencil size={14} className="mr-2"/>
                        Edit details
                    </Button>
                )}
            </div>

            {!isEditing && (
                <div className="grid grid-cols-2 sm:flex sm:flex-row gap-2 w-full">
                    <Button
                        type="button"
                        variant="outline"
                        onClick={handleDownloadPdf}
                        disabled={isDownloading}
                        className="w-full sm:w-auto h-10 text-xs sm:text-sm px-3"
                    >
                        <Download size={15} className="mr-2 shrink-0"/>
                        <span className="truncate">{isDownloading ? 'Generating...' : 'PDF'}</span>
                    </Button>

                    {canSend && (
                        <Button
                            type="button"
                            onClick={() => handleTransition(
                                () => sendInvoice({id, version: invoice.version}).unwrap(),
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
                                onSubmit={async (idempotencyKey: string, data: PaymentRequest) => {
                                    await recordPayment({invoiceId: id, idempotencyKey, body: data}).unwrap();
                                }}
                            />
                        </div>
                    )}

                    {invoice.status === 'SENT' && (
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => handleTransition(
                                () => markPaid({id, version: invoice.version}).unwrap(),
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
                            type="button"
                            variant="destructive"
                            onClick={() => handleTransition(
                                () => cancelInvoice({id, version: invoice.version}).unwrap(),
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
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <Card className="border-muted/40">
                    <CardHeader className="pb-3 px-4 sm:px-6">
                        <CardTitle className="text-base sm:text-lg">Details</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-4 text-sm px-4 sm:px-6">
                        <div className="flex flex-col gap-3">
                            <div className="flex justify-between items-center gap-4">
                                <span className="text-muted-foreground shrink-0">Issue date</span>
                                {isEditing ? (
                                    <div className="flex flex-col items-end">
                                        <Input type="date" {...register('issueDate')} className="h-8 max-w-40 text-xs"/>
                                        {errors.issueDate && <span
                                            className="text-[10px] text-destructive">{errors.issueDate.message}</span>}
                                    </div>
                                ) : (
                                    <span className="font-medium text-foreground">{invoice.issueDate}</span>
                                )}
                            </div>

                            <div className="flex justify-between items-center gap-4">
                                <span className="text-muted-foreground shrink-0">Due date</span>
                                {isEditing ? (
                                    <div className="flex flex-col items-end">
                                        <Input type="date" {...register('dueDate')} className="h-8 max-w-40 text-xs"/>
                                        {errors.dueDate && <span
                                            className="text-[10px] text-destructive">{errors.dueDate.message}</span>}
                                    </div>
                                ) : (
                                    <span className="font-medium text-foreground">{invoice.dueDate}</span>
                                )}
                            </div>

                            <div className="flex justify-between items-center gap-4">
                                <span className="text-muted-foreground shrink-0">Tax rate</span>
                                {isEditing ? (
                                    <div className="flex flex-col items-end w-full max-w-48">
                                        <Controller
                                            control={control}
                                            name="taxRate"
                                            render={({field}) => (
                                                <Select
                                                    value={
                                                        field.value === 0.2 ? "0.20" :
                                                            field.value === 0.1 ? "0.10" :
                                                                field.value === 0.055 ? "0.055" :
                                                                    field.value === 0 ? "0" :
                                                                        String(field.value || "0.20")
                                                    }
                                                    onValueChange={(v) => field.onChange(parseFloat(v))}
                                                >
                                                    <SelectTrigger className="h-8 text-xs bg-background w-full">
                                                        <SelectValue placeholder="Select tax rate"/>
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="0">0% — No tax</SelectItem>
                                                        <SelectItem value="0.055">5.5% — Reduced</SelectItem>
                                                        <SelectItem value="0.10">10% — Intermediate</SelectItem>
                                                        <SelectItem value="0.20">20% — Standard VAT</SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            )}
                                        />
                                        {errors.taxRate && <span
                                            className="text-[10px] text-destructive mt-0.5">{errors.taxRate.message}</span>}
                                    </div>
                                ) : (
                                    <span className="font-medium text-foreground">
                                        {invoice.taxRate === 0.055 ? '5.5%' : `${invoice.taxRate * 100}%`}
                                    </span>
                                )}
                            </div>

                            <div className="flex justify-between items-center gap-4">
                                <span className="text-muted-foreground shrink-0">Client</span>
                                <span className="font-medium text-right text-foreground">{invoice.clientName}</span>
                            </div>
                        </div>
                        <Separator/>
                        <div className="space-y-2">
                            <span className="text-xs font-semibold text-muted-foreground">Notes</span>
                            {isEditing ? (
                                <Textarea{...register('notes')} placeholder="Add payment context..."
                                         className="text-xs min-h-20"/>
                            ) : (
                                <p className="text-muted-foreground leading-relaxed wrap-break-word text-xs sm:text-sm">
                                    {invoice.notes ||
                                        <span className="italic text-muted-foreground/50">No notes provided</span>}
                                </p>
                            )}
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-muted/40">
                    <CardHeader className="pb-3 px-4 sm:px-6">
                        <CardTitle className="text-base sm:text-lg">Financials</CardTitle></CardHeader>
                    <CardContent className="space-y-3 text-sm px-4 sm:px-6">
                        {[
                            ['Subtotal', formatCurrency(invoice.subtotal)],
                            [`Tax (${(invoice.taxRate * 100).toFixed(0)}%)`, formatCurrency(invoice.taxAmount)]
                        ].map(([label, value]) => (
                            <div key={label} className="flex justify-between gap-4">
                                <span className="text-muted-foreground shrink-0">{label}</span>
                                <span className="text-right text-foreground">{value}</span>
                            </div>
                        ))}
                        <Separator/>
                        <div className="flex justify-between font-bold text-base gap-4 text-foreground">
                            <span>Total</span>
                            <span className="text-right">{formatCurrency(invoice.total)}</span>
                        </div>
                        <div className="flex justify-between text-muted-foreground gap-4">
                            <span>Amount paid</span>
                            <span
                                className="text-green-600 text-right font-medium">{formatCurrency(invoice.amountPaid)}</span>
                        </div>
                        <div className="flex justify-between font-semibold gap-4 text-foreground">
                            <span>Balance due</span>
                            <span
                                className={`text-right ${invoice.remainingBalance > 0 ? 'text-destructive font-bold' : 'text-muted-foreground'}`}>
                                {formatCurrency(invoice.remainingBalance)}
                            </span>
                        </div>
                    </CardContent>
                </Card>
            </div>

            <Card className="border-muted/40">
                <CardHeader className="flex flex-row items-center justify-between pb-3 px-4 sm:px-6">
                    <CardTitle className="text-base sm:text-lg">Line Items</CardTitle>
                    {isEditing && (
                        <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() => append({
                                description: '',
                                quantity: 1,
                                unitPrice: 0,
                                discountPct: 0,
                                position: fields.length
                            })}
                            className="h-8 text-xs px-2.5"
                        >
                            <Plus size={14} className="mr-1"/> Add Item
                        </Button>
                    )}
                </CardHeader>
                <CardContent className="px-2 sm:px-6">
                    {isEditing ? (
                        <div className="space-y-6">
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm min-w-155">
                                    <thead>
                                    <tr className="border-b text-muted-foreground text-xs font-semibold text-left">
                                        <th className="pb-2 pl-1 w-2/5">Description *</th>
                                        <th className="pb-2 text-right w-16">Qty</th>
                                        <th className="pb-2 text-right w-28">Unit Price (€)</th>
                                        <th className="pb-2 text-right w-24">Discount (%)</th>
                                        <th className="w-12"></th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {fields.map((field, idx) => (
                                        <tr key={field.id} className="border-b last:border-0 hover:bg-muted/5">
                                            <td className="py-2 pr-2 align-top">
                                                <Input
                                                    {...register(`lineItems.${idx}.description`)}
                                                    placeholder="Item description"
                                                    className="h-9 text-xs bg-background"
                                                />
                                                {errors.lineItems?.[idx]?.description && (
                                                    <p className="text-[10px] text-destructive mt-0.5">
                                                        {errors.lineItems[idx]?.description?.message}
                                                    </p>
                                                )}
                                            </td>
                                            <td className="py-2 px-1 align-top">
                                                <Input
                                                    type="number"
                                                    {...register(`lineItems.${idx}.quantity`, {valueAsNumber: true})}
                                                    className="h-9 text-right text-xs bg-background"
                                                />
                                            </td>
                                            <td className="py-2 px-1 align-top">
                                                <Input
                                                    type="number"
                                                    step="0.01"
                                                    {...register(`lineItems.${idx}.unitPrice`, {valueAsNumber: true})}
                                                    className="h-9 text-right text-xs bg-background"
                                                />
                                            </td>
                                            <td className="py-2 px-1 align-top">
                                                <Input
                                                    type="number"
                                                    step="1"
                                                    defaultValue={Math.round((invoice.lineItems[idx]?.discountPct ?? 0) * 100)}
                                                    onChange={(e) => {
                                                        const val = (parseInt(e.target.value) || 0) / 100;
                                                        register(`lineItems.${idx}.discountPct`).onChange({
                                                            target: {name: `lineItems.${idx}.discountPct`, value: val}
                                                        });
                                                    }}
                                                    className="h-9 text-right text-xs bg-background"
                                                />
                                            </td>
                                            <td className="py-2 text-center align-top">
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="icon"
                                                    onClick={() => {
                                                        if (fields.length <= 1) {
                                                            toast.error("Invoices must contain at least one line item.");
                                                            return;
                                                        }
                                                        remove(idx);
                                                    }}
                                                    className="h-8 w-8 text-destructive hover:bg-destructive/10"
                                                >
                                                    <Trash2 size={14}/>
                                                </Button>
                                            </td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>

                            <div className="flex gap-2 pt-2 border-t">
                                <Button type="submit" disabled={isUpdating} className="h-9 text-xs sm:text-sm">
                                    <Check size={14} className="mr-2"/>
                                    {isUpdating ? 'Saving...' : 'Save changes'}
                                </Button>
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={handleCancelEdit}
                                    className="h-9 text-xs sm:text-sm"
                                >
                                    <X size={14} className="mr-2"/>
                                    Cancel
                                </Button>
                            </div>
                        </div>
                    ) : (
                        <>
                            <div className="block md:hidden space-y-4">
                                {invoice.lineItems.map((item) => (
                                    <div key={item.id} className="p-3.5 border rounded-lg bg-muted/10 space-y-2">
                                        <div className="font-semibold text-sm text-foreground wrap-break-word">
                                            {item.description}
                                        </div>
                                        <div
                                            className="grid grid-cols-3 gap-2 pt-1.5 border-t text-xs text-muted-foreground">
                                            <div>
                                                <p className="font-medium text-[10px] uppercase tracking-wider">Qty</p>
                                                <p className="text-foreground font-medium mt-0.5">{item.quantity}</p>
                                            </div>
                                            <div>
                                                <p className="font-medium text-[10px] uppercase tracking-wider">Unit
                                                    Price</p>
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
                                            <span
                                                className="text-xs font-semibold text-muted-foreground">Item Total</span>
                                            <span
                                                className="text-sm font-bold text-primary">{formatCurrency(item.lineTotal)}</span>
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
                                            <td className="py-3 pr-4 max-w-xs truncate text-foreground">{item.description}</td>
                                            <td className="text-right py-3 text-foreground">{item.quantity}</td>
                                            <td className="text-right py-3 text-foreground">{formatCurrency(item.unitPrice)}</td>
                                            <td className="text-right py-3 text-foreground">
                                                {item.discountPct > 0 ? `${(item.discountPct * 100).toFixed(0)}%` : '—'}
                                            </td>
                                            <td className="text-right py-3 font-semibold text-foreground">{formatCurrency(item.lineTotal)}</td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </div>
                        </>
                    )}
                </CardContent>
            </Card>

            {payments.length > 0 && !isEditing && (
                <Card className="border-muted/40">
                    <CardHeader className="pb-3 px-4 sm:px-6"><CardTitle className="text-base sm:text-lg">Payment
                        History</CardTitle></CardHeader>
                    <CardContent className="space-y-1 px-4 sm:px-6">
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
        </form>
    );
}