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
import {ArrowLeft, Pencil, Check, X, Plus} from 'lucide-react';
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
        <div className="space-y-6">
            <div className="flex items-center gap-4">
                <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => navigate('/clients')}
                >
                    <ArrowLeft size={18}/>
                </Button>
                <div className="flex-1">
                    <h1 className="text-3xl font-bold tracking-tight">{client.name}</h1>
                    <p className="text-muted-foreground mt-1">
                        Client since {new Date(client.createdAt).toLocaleDateString('fr-FR')}
                    </p>
                </div>
                {!isEditing && (
                    <Button
                        variant="outline"
                        onClick={() => setIsEditing(true)}
                    >
                        <Pencil size={15} className="mr-2"/>
                        Edit
                    </Button>
                )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="md:col-span-2">
                    <Card>
                        <CardHeader>
                            <CardTitle>Client details</CardTitle>
                        </CardHeader>
                        <CardContent>
                            {isEditing ? (
                                <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                                    <div className="space-y-2">
                                        <Label htmlFor="name">Name *</Label>
                                        <Input id="name" {...register('name')} />
                                        {errors.name && (
                                            <p className="text-sm text-destructive">
                                                {errors.name.message}
                                            </p>
                                        )}
                                    </div>

                                    <div className="space-y-2">
                                        <Label htmlFor="email">Email</Label>
                                        <Input id="email" type="email" {...register('email')} />
                                        {errors.email && (
                                            <p className="text-sm text-destructive">
                                                {errors.email.message}
                                            </p>
                                        )}
                                    </div>

                                    <div className="grid grid-cols-2 gap-3">
                                        <div className="space-y-2">
                                            <Label htmlFor="phone">Phone</Label>
                                            <Input id="phone" {...register('phone')} />
                                        </div>
                                        <div className="space-y-2">
                                            <Label htmlFor="vatNumber">VAT number</Label>
                                            <Input id="vatNumber" {...register('vatNumber')} />
                                        </div>
                                    </div>

                                    <div className="space-y-2">
                                        <Label htmlFor="address">Address</Label>
                                        <Input id="address" {...register('address')} />
                                    </div>

                                    <div className="flex gap-2">
                                        <Button type="submit" disabled={isUpdating}>
                                            <Check size={15} className="mr-2"/>
                                            {isUpdating ? 'Saving...' : 'Save changes'}
                                        </Button>
                                        <Button
                                            type="button"
                                            variant="outline"
                                            onClick={handleCancelEdit}
                                        >
                                            <X size={15} className="mr-2"/>
                                            Cancel
                                        </Button>
                                    </div>
                                </form>
                            ) : (
                                <dl className="space-y-3 text-sm">
                                    {[
                                        ['Email', client.email],
                                        ['Phone', client.phone],
                                        ['Address', client.address],
                                        ['VAT number', client.vatNumber]
                                    ].map(([label, value]) => (
                                        <div key={label} className="flex gap-4">
                                            <dt className="w-28 text-muted-foreground shrink-0">
                                                {label}
                                            </dt>
                                            <dd className="font-medium">{value ?? '—'}</dd>
                                        </div>
                                    ))}
                                </dl>
                            )}
                        </CardContent>
                    </Card>
                </div>

                <div className="space-y-4">
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm text-muted-foreground font-medium">
                                Total invoiced
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            <p className="text-2xl font-bold">
                                {formatCurrency(totalInvoiced)}
                            </p>
                            <p className="text-xs text-muted-foreground mt-1">
                                {invoices?.totalElements ?? 0} invoices
                            </p>
                        </CardContent>
                    </Card>

                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm text-muted-foreground font-medium">
                                Outstanding balance
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            <p className={`text-2xl font-bold ${
                                totalOutstanding > 0 ? 'text-destructive' : 'text-muted-foreground'
                            }`}>
                                {formatCurrency(totalOutstanding)}
                            </p>
                            <p className="text-xs text-muted-foreground mt-1">
                                Unpaid, sent and overdue
                            </p>
                        </CardContent>
                    </Card>
                </div>
            </div>

            <Card>
                <CardHeader className="flex flex-row items-center justify-between">
                    <CardTitle>Invoices</CardTitle>
                    <Button
                        size="sm"
                        onClick={() => navigate(`/invoices/new?clientId=${id}`)}
                    >
                        <Plus size={15} className="mr-1"/>
                        New invoice
                    </Button>
                </CardHeader>
                <CardContent>
                    {!invoices?.content.length ? (
                        <p className="text-center py-6 text-muted-foreground text-sm">
                            No invoices for this client yet
                        </p>
                    ) : (
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
                                                <span className="text-destructive font-medium">
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
                    )}
                </CardContent>
            </Card>
        </div>
    );
}