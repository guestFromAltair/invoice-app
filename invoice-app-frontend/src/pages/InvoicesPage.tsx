import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useGetInvoicesQuery} from '../store/apiSlice';
import type {InvoiceStatus} from '@/types';
import {Button} from '../components/ui/button';
import {Badge} from '../components/ui/badge';
import {Card, CardContent, CardHeader, CardTitle} from '../components/ui/card';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue
} from '../components/ui/select';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow
} from '../components/ui/table';
import {Plus, FileText, Calendar, Wallet} from 'lucide-react';

const statusVariant: Record<InvoiceStatus, 'default' | 'secondary' | 'destructive' | 'outline'> = {
    DRAFT: 'secondary',
    SENT: 'default',
    PAID: 'outline',
    OVERDUE: 'destructive',
    CANCELLED: 'secondary'
};

const statusLabel: Record<InvoiceStatus, string> = {
    DRAFT: 'Draft',
    SENT: 'Sent',
    PAID: 'Paid',
    OVERDUE: 'Overdue',
    CANCELLED: 'Cancelled'
};

export default function InvoicesPage() {
    const navigate = useNavigate();
    const [page, setPage] = useState(0);
    const [statusFilter, setStatusFilter] = useState<InvoiceStatus | undefined>();

    const {data, isLoading, isFetching} = useGetInvoicesQuery({
        page,
        size: 20,
        status: statusFilter
    });

    const formatCurrency = (amount: number) =>
        new Intl.NumberFormat('fr-FR', {
            style: 'currency',
            currency: 'EUR'
        }).format(amount);

    return (
        <div className="space-y-6 max-w-full overflow-hidden p-0.5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl sm:text-3xl font-bold tracking-tight">Invoices</h1>
                    <p className="text-muted-foreground mt-1 text-sm">
                        {data?.totalElements ?? 0} invoices total
                    </p>
                </div>
                <Button onClick={() => navigate('/invoices/new')} className="w-full sm:w-auto h-10">
                    <Plus size={16} className="mr-2"/>
                    New Invoice
                </Button>
            </div>

            <div className="flex gap-3">
                <Select
                    value={statusFilter ?? 'ALL'}
                    onValueChange={(value) => {
                        setStatusFilter(value === 'ALL'
                            ? undefined
                            : value as InvoiceStatus);
                        setPage(0);
                    }}
                >
                    <SelectTrigger className="w-full sm:w-40 h-10">
                        <SelectValue placeholder="All statuses"/>
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="ALL">All statuses</SelectItem>
                        <SelectItem value="DRAFT">Draft</SelectItem>
                        <SelectItem value="SENT">Sent</SelectItem>
                        <SelectItem value="PAID">Paid</SelectItem>
                        <SelectItem value="OVERDUE">Overdue</SelectItem>
                        <SelectItem value="CANCELLED">Cancelled</SelectItem>
                    </SelectContent>
                </Select>
            </div>

            <Card className="border-muted/40">
                <CardHeader className="pb-3 px-4 sm:px-6">
                    <CardTitle className="flex items-center gap-2 text-base sm:text-lg">
                        <FileText size={18} className="text-muted-foreground"/>
                        Invoice list
                    </CardTitle>
                </CardHeader>
                <CardContent className="px-2 sm:px-6">
                    <div className={isFetching ? 'opacity-60 transition-opacity' : ''}>
                        <div className="block md:hidden space-y-3">
                            {isLoading ? (
                                <div className="text-center py-8 text-muted-foreground text-sm">
                                    Loading invoices...
                                </div>
                            ) : data?.content.length === 0 ? (
                                <div className="text-center py-8 text-muted-foreground text-sm">
                                    No invoices found
                                </div>
                            ) : (
                                data?.content.map((invoice) => (
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
                                                {statusLabel[invoice.status]}
                                            </Badge>
                                        </div>

                                        <div className="space-y-1">
                                            <p className="text-xs text-muted-foreground font-medium">Client</p>
                                            <p className="text-sm font-semibold text-foreground truncate">
                                                {invoice.clientName}
                                            </p>
                                        </div>

                                        <div className="grid grid-cols-2 gap-3 pt-2.5 border-t text-xs">
                                            <div className="space-y-1">
                                                <span className="flex items-center gap-1 text-muted-foreground text-[10px]">
                                                    <Calendar size={12}/>
                                                    Due date
                                                </span>
                                                <span className="font-medium text-foreground">{invoice.dueDate}</span>
                                            </div>
                                            <div className="space-y-1 text-right">
                                                <span className="flex items-center gap-1 justify-end text-muted-foreground text-[10px]">
                                                    <Wallet size={12}/>
                                                    Total / Balance Due
                                                </span>
                                                <div className="mt-0.5">
                                                    <span className="font-semibold text-foreground mr-1">
                                                        {formatCurrency(invoice.total)}
                                                    </span>
                                                    {invoice.remainingBalance > 0 && (
                                                        <span className="block text-[10px] text-destructive font-bold">
                                                            ({formatCurrency(invoice.remainingBalance)} left)
                                                        </span>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>

                        <div className="hidden md:block overflow-x-auto">
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Number</TableHead>
                                        <TableHead>Client</TableHead>
                                        <TableHead>Status</TableHead>
                                        <TableHead>Issue date</TableHead>
                                        <TableHead>Due date</TableHead>
                                        <TableHead className="text-right">Total</TableHead>
                                        <TableHead className="text-right">Balance due</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {isLoading ? (
                                        <TableRow>
                                            <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                                                Loading invoices...
                                            </TableCell>
                                        </TableRow>
                                    ) : data?.content.length === 0 ? (
                                        <TableRow>
                                            <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                                                No invoices found
                                            </TableCell>
                                        </TableRow>
                                    ) : (
                                        data?.content.map((invoice) => (
                                            <TableRow
                                                key={invoice.id}
                                                className="cursor-pointer hover:bg-muted/50"
                                                onClick={() => navigate(`/invoices/${invoice.id}`)}
                                            >
                                                <TableCell className="font-mono font-medium">
                                                    {invoice.invoiceNumber}
                                                </TableCell>
                                                <TableCell>{invoice.clientName}</TableCell>
                                                <TableCell>
                                                    <Badge variant={statusVariant[invoice.status]}>
                                                        {statusLabel[invoice.status]}
                                                    </Badge>
                                                </TableCell>
                                                <TableCell>{invoice.issueDate}</TableCell>
                                                <TableCell>{invoice.dueDate}</TableCell>
                                                <TableCell className="text-right font-medium">
                                                    {formatCurrency(invoice.total)}
                                                </TableCell>
                                                <TableCell className="text-right">
                                                    {invoice.remainingBalance > 0
                                                        ? <span className="text-destructive font-semibold">
                                                            {formatCurrency(invoice.remainingBalance)}
                                                          </span>
                                                        : <span className="text-muted-foreground">—</span>
                                                    }
                                                </TableCell>
                                            </TableRow>
                                        ))
                                    )}
                                </TableBody>
                            </Table>
                        </div>
                    </div>

                    {data && data.totalPages > 1 && (
                        <div
                            className="flex flex-col sm:flex-row items-center justify-between gap-3 mt-5 pt-3 border-t">
                            <p className="text-xs sm:text-sm text-muted-foreground">
                                Page {data.number + 1} of {data.totalPages}
                            </p>
                            <div className="flex gap-2 w-full sm:w-auto">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setPage(p => p === 0 ? 0 : p - 1)}
                                    disabled={data.first}
                                    className="flex-1 sm:flex-initial"
                                >
                                    Previous
                                </Button>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setPage(p => p === data.totalPages - 1 ? p : p + 1)}
                                    disabled={data.last}
                                    className="flex-1 sm:flex-initial"
                                >
                                    Next
                                </Button>
                            </div>
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}