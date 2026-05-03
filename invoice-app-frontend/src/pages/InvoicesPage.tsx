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
import {Plus, FileText} from 'lucide-react';

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
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold tracking-tight">Invoices</h1>
                    <p className="text-muted-foreground mt-1">
                        {data?.totalElements ?? 0} invoices total
                    </p>
                </div>
                <Button onClick={() => navigate('/invoices/new')}>
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
                    <SelectTrigger className="w-40">
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

            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <FileText size={20}/>
                        Invoice list
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <div className={isFetching ? 'opacity-60 transition-opacity' : ''}>
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
                                                    ? <span className="text-destructive font-medium">
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

                    {data && data.totalPages > 1 && (
                        <div className="flex items-center justify-between mt-4">
                            <p className="text-sm text-muted-foreground">
                                Page {data.number + 1} of {data.totalPages}
                            </p>
                            <div className="flex gap-2">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setPage(p => p === 0 ? 0 : p - 1)}
                                    disabled={data.first}
                                >
                                    Previous
                                </Button>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setPage(p => p === data.totalPages - 1 ? p : p + 1)}
                                    disabled={data.last}
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