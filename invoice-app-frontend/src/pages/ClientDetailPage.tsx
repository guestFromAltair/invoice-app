import {useState} from 'react';
import {useParams, useNavigate} from 'react-router-dom';
import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {
    useGetClientQuery,
    useUpdateClientMutation,
    useGetInvoicesQuery
} from '../store/apiSlice';
import {Button} from '../components/ui/button';
import {Input} from '../components/ui/input';
import {Label} from '../components/ui/label';
import {Badge} from '../components/ui/badge';
import {Card, CardContent, CardHeader, CardTitle} from '../components/ui/card';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow
} from '../components/ui/table';
import {ArrowLeft, Pencil, Check, X, Plus, Calendar, Wallet} from 'lucide-react';
import {toast} from 'sonner';
import type {InvoiceStatus} from '@/types';
import {skipToken} from '@reduxjs/toolkit/query';

const clientSchema = z.object({
    name: z.string().min(1, 'Name is required'),
    email: z.email('Invalid email').optional().or(z.literal('')),
    phone: z.string().optional(),
    address: z.string().optional(),
    vatNumber: z.string().optional()
});

type ClientFormData = z.infer<typeof clientSchema>;

const statusVariant: Record<InvoiceStatus, 'default' | 'secondary' | 'destructive' | 'outline'> = {
    DRAFT: 'secondary',
    SENT: 'default',
    PAID: 'outline',
    OVERDUE: 'destructive',
    CANCELLED: 'secondary'
};

const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('fr-FR', {style: 'currency', currency: 'EUR'})
        .format(amount);

export default function ClientDetailPage() {
    const {id} = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [isEditing, setIsEditing] = useState(false);

    const {data: client, isLoading} = useGetClientQuery(id ?? skipToken);
    const [updateClient, {isLoading: isUpdating}] = useUpdateClientMutation();

    const {data: invoices} = useGetInvoicesQuery(id ? {clientId: id, size: 50} : skipToken);

    const {
        register,
        handleSubmit,
        reset,
        formState: {errors}
    } = useForm<ClientFormData>({
        resolver: zodResolver(clientSchema),
        values: client
            ? {
                name: client.name,
                email: client.email ?? '',
                phone: client.phone ?? '',
                address: client.address ?? '',
                vatNumber: client.vatNumber ?? ''
            }
            : undefined
    });

    if (!id) {
        return (
            <div className="text-center py-12 text-muted-foreground">
                Missing client id
            </div>
        );
    }

    const onSubmit = async (data: ClientFormData) => {
        try {
            await updateClient({
                id: id,
                body: {
                    name: data.name,
                    email: data.email || undefined,
                    phone: data.phone || undefined,
                    address: data.address || undefined,
                    vatNumber: data.vatNumber || undefined
                },
            }).unwrap();
            toast.success('Client updated');
            setIsEditing(false);
        } catch {
            toast.error('Failed to update client');
        }
    };

    const handleCancelEdit = () => {
        reset();
        setIsEditing(false);
    };

    if (isLoading) return (
        <div className="flex items-center justify-center h-64 text-muted-foreground">
            Loading client...
        </div>
    );

    if (!client) return (
        <div className="text-center py-12 text-muted-foreground">
            Client not found
        </div>
    );

    const totalInvoiced = invoices?.content
        .reduce((sum, i) => sum + i.total, 0) ?? 0;
    const totalOutstanding = invoices?.content
        .filter(i => i.status === 'SENT' || i.status === 'OVERDUE')
        .reduce((sum, i) => sum + i.remainingBalance, 0) ?? 0;

    return (
        <div className="space-y-6 max-w-full overflow-hidden p-0.5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="flex items-start gap-3">
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => navigate('/clients')}
                        className="h-9 w-9 shrink-0 mt-0.5"
                    >
                        <ArrowLeft size={18}/>
                    </Button>
                    <div className="min-w-0">
                        <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground truncate">
                            {client.name}
                        </h1>
                        <p className="text-muted-foreground mt-1 text-xs sm:text-sm">
                            Client since {new Date(client.createdAt).toLocaleDateString('fr-FR')}
                        </p>
                    </div>
                </div>
                {!isEditing && (
                    <Button
                        variant="outline"
                        onClick={() => setIsEditing(true)}
                        className="w-full sm:w-auto h-9 text-xs sm:text-sm"
                    >
                        <Pencil size={14} className="mr-2"/>
                        Edit details
                    </Button>
                )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="md:col-span-2">
                    <Card className="border-muted/40">
                        <CardHeader className="px-4 sm:px-6">
                            <CardTitle className="text-base sm:text-lg">Client details</CardTitle>
                        </CardHeader>
                        <CardContent className="px-4 sm:px-6">
                            {isEditing ? (
                                <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                                    <div className="space-y-2">
                                        <Label htmlFor="name" className="text-xs font-semibold">Name *</Label>
                                        <Input id="name" {...register('name')} className="h-10"/>
                                        {errors.name && (
                                            <p className="text-xs text-destructive">
                                                {errors.name.message}
                                            </p>
                                        )}
                                    </div>

                                    <div className="space-y-2">
                                        <Label htmlFor="email" className="text-xs font-semibold">Email</Label>
                                        <Input id="email" type="email" {...register('email')} className="h-10"/>
                                        {errors.email && (
                                            <p className="text-xs text-destructive">
                                                {errors.email.message}
                                            </p>
                                        )}
                                    </div>

                                    <div className="grid grid-cols-2 gap-3">
                                        <div className="space-y-2">
                                            <Label htmlFor="phone" className="text-xs font-semibold">Phone</Label>
                                            <Input id="phone" {...register('phone')} className="h-10"/>
                                        </div>
                                        <div className="space-y-2">
                                            <Label htmlFor="vatNumber" className="text-xs font-semibold">VAT
                                                number</Label>
                                            <Input id="vatNumber" {...register('vatNumber')} className="h-10"/>
                                        </div>
                                    </div>

                                    <div className="space-y-2">
                                        <Label htmlFor="address" className="text-xs font-semibold">Address</Label>
                                        <Input id="address" {...register('address')} className="h-10"/>
                                    </div>

                                    <div className="flex gap-2 pt-2">
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
                                </form>
                            ) : (
                                <dl className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs sm:text-sm pt-1">
                                    {[
                                        ['Email', client.email],
                                        ['Phone', client.phone],
                                        ['Address', client.address],
                                        ['VAT number', client.vatNumber]
                                    ].map(([label, value]) => (
                                        <div key={label} className="border-b last:border-0 sm:border-0 pb-2 sm:pb-0">
                                            <dt className="text-muted-foreground text-xs font-medium mb-0.5">
                                                {label}
                                            </dt>
                                            <dd className="font-semibold text-foreground truncate">
                                                {value || '—'}
                                            </dd>
                                        </div>
                                    ))}
                                </dl>
                            )}
                        </CardContent>
                    </Card>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-1 gap-4">
                    <Card className="border-muted/40">
                        <CardHeader className="pb-2 px-4">
                            <CardTitle className="text-xs text-muted-foreground font-semibold uppercase tracking-wider">
                                Total invoiced
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="px-4 pb-4">
                            <p className="text-2xl font-bold text-foreground">
                                {formatCurrency(totalInvoiced)}
                            </p>
                            <p className="text-[11px] text-muted-foreground mt-1">
                                {invoices?.totalElements ?? 0} invoices
                            </p>
                        </CardContent>
                    </Card>

                    <Card className="border-muted/40">
                        <CardHeader className="pb-2 px-4">
                            <CardTitle className="text-xs text-muted-foreground font-semibold uppercase tracking-wider">
                                Outstanding balance
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="px-4 pb-4">
                            <p className={`text-2xl font-bold ${
                                totalOutstanding > 0 ? 'text-destructive' : 'text-muted-foreground'
                            }`}>
                                {formatCurrency(totalOutstanding)}
                            </p>
                            <p className="text-[11px] text-muted-foreground mt-1">
                                Unpaid, sent and overdue
                            </p>
                        </CardContent>
                    </Card>
                </div>
            </div>

            <Card className="border-muted/40">
                <CardHeader className="flex flex-row items-center justify-between pb-3 px-4 sm:px-6">
                    <CardTitle className="text-base sm:text-lg">Invoices</CardTitle>
                    <Button
                        size="sm"
                        onClick={() => navigate(`/invoices/new?clientId=${id}`)}
                        className="h-8 text-xs px-3"
                    >
                        <Plus size={14} className="mr-1"/>
                        New invoice
                    </Button>
                </CardHeader>
                <CardContent className="px-2 sm:px-6">
                    {!invoices?.content.length ? (
                        <p className="text-center py-8 text-muted-foreground text-xs sm:text-sm">
                            No invoices for this client yet
                        </p>
                    ) : (
                        <>
                            <div className="block md:hidden space-y-3 px-2">
                                {invoices.content.map((invoice) => (
                                    <div
                                        key={invoice.id}
                                        onClick={() => navigate(`/invoices/${invoice.id}`)}
                                        className="p-3.5 border rounded-lg bg-card hover:bg-muted/5 transition-colors active:bg-muted/10 space-y-3 cursor-pointer"
                                    >
                                        <div className="flex items-center justify-between">
                                            <span className="font-mono font-bold text-sm text-foreground">
                                                {invoice.invoiceNumber}
                                            </span>
                                            <Badge variant={statusVariant[invoice.status]}
                                                   className="text-[10px] px-2 py-0.5">
                                                {invoice.status}
                                            </Badge>
                                        </div>

                                        <div className="grid grid-cols-2 gap-3 pt-2 border-t text-xs">
                                            <div className="space-y-1">
                                                <span
                                                    className="flex items-center gap-1 text-muted-foreground text-[10px]">
                                                    <Calendar size={12}/>
                                                    Due Date
                                                </span>
                                                <span className="font-medium text-foreground">{invoice.dueDate}</span>
                                            </div>
                                            <div className="space-y-1 text-right">
                                                <span
                                                    className="flex items-center gap-1 justify-end text-muted-foreground text-[10px]">
                                                    <Wallet size={12}/>
                                                    Balance / Total
                                                </span>
                                                <div className="mt-0.5">
                                                    <span className="font-semibold text-foreground">
                                                        {formatCurrency(invoice.total)}
                                                    </span>
                                                    {invoice.remainingBalance > 0 && (
                                                        <span className="block text-[10px] text-destructive font-bold">
                                                            ({formatCurrency(invoice.remainingBalance)} balance)
                                                        </span>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>

                            <div className="hidden md:block overflow-x-auto">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Number</TableHead>
                                            <TableHead>Status</TableHead>
                                            <TableHead>Due date</TableHead>
                                            <TableHead className="text-right">Total</TableHead>
                                            <TableHead className="text-right">Balance</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {invoices.content.map((invoice) => (
                                            <TableRow
                                                key={invoice.id}
                                                className="cursor-pointer hover:bg-muted/50"
                                                onClick={() => navigate(`/invoices/${invoice.id}`)}
                                            >
                                                <TableCell className="font-mono font-medium text-primary">
                                                    {invoice.invoiceNumber}
                                                </TableCell>
                                                <TableCell>
                                                    <Badge variant={statusVariant[invoice.status]}>
                                                        {invoice.status}
                                                    </Badge>
                                                </TableCell>
                                                <TableCell className="text-muted-foreground">
                                                    {invoice.dueDate}
                                                </TableCell>
                                                <TableCell className="text-right font-medium">
                                                    {formatCurrency(invoice.total)}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    {invoice.remainingBalance > 0 ? (
                                                        <span className="text-destructive font-semibold">
                                                          {formatCurrency(invoice.remainingBalance)}
                                                        </span>
                                                    ) : (
                                                        <span className="text-muted-foreground">—</span>
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </div>
                        </>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}