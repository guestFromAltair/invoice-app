import {useSelector} from 'react-redux';
import {useNavigate} from 'react-router-dom';
import {useGetInvoicesQuery} from '../store/apiSlice';
import {selectCurrentUser} from '../store/authSlice';
import {Card, CardContent, CardHeader, CardTitle} from '../components/ui/card';
import {Badge} from '../components/ui/badge';
import {Button} from '../components/ui/button';
import {
    FileText,
    Clock,
    AlertTriangle,
    TrendingUp,
    Plus,
    ArrowRight,
} from 'lucide-react';

import type {InvoiceStatus, Invoice} from '@/types';

const statusVariant: Record<InvoiceStatus, 'default' | 'secondary' | 'destructive' | 'outline'> = {
    DRAFT: 'secondary',
    SENT: 'default',
    PAID: 'outline',
    OVERDUE: 'destructive',
    CANCELLED: 'secondary',
};

const formatCurrency = (amount: number) =>
    new Intl.NumberFormat('fr-FR', {
        style: 'currency',
        currency: 'EUR',
    }).format(amount);

export default function DashboardPage() {
    const navigate = useNavigate();
    const user = useSelector(selectCurrentUser);

    const {data: allInvoices} = useGetInvoicesQuery({size: 5});
    const {data: sentInvoices} = useGetInvoicesQuery({status: 'SENT', size: 1});
    const {data: overdueInvoices} = useGetInvoicesQuery({status: 'OVERDUE', size: 1});
    const {data: paidInvoices} = useGetInvoicesQuery({status: 'PAID', size: 1});

    const outstandingBalance = allInvoices?.content
        .filter(i => i.status === 'SENT' || i.status === 'OVERDUE')
        .reduce((sum, i) => sum + i.remainingBalance, 0) ?? 0;

    const summaryCards = [
        {
            title: 'Total invoiced',
            value: formatCurrency(
                allInvoices?.content.reduce((sum, i) => sum + i.total, 0) ?? 0
            ),
            icon: TrendingUp,
            description: `${allInvoices?.totalElements ?? 0} invoices total`,
            colour: 'text-blue-600'
        },
        {
            title: 'Awaiting payment',
            value: String(sentInvoices?.totalElements ?? 0),
            icon: Clock,
            description: 'Invoices sent but unpaid',
            colour: 'text-yellow-600'
        },
        {
            title: 'Overdue',
            value: String(overdueInvoices?.totalElements ?? 0),
            icon: AlertTriangle,
            description: 'Past their due date',
            colour: 'text-red-600'
        },
        {
            title: 'Paid this period',
            value: String(paidInvoices?.totalElements ?? 0),
            icon: FileText,
            description: 'Fully settled invoices',
            colour: 'text-green-600'
        }
    ];

    return (
        <div className="space-y-8">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
                    <p className="text-muted-foreground mt-1">
                        Welcome back, {user.email}
                    </p>
                </div>
                <Button onClick={() => navigate('/invoices/new')}>
                    <Plus size={16} className="mr-2"/>
                    New Invoice
                </Button>
            </div>

            {outstandingBalance > 0 && (
                <Card className="border-yellow-200 bg-yellow-50 dark:bg-yellow-950 dark:border-yellow-800">
                    <CardContent className="flex items-center justify-between py-4">
                        <div>
                            <p className="text-sm font-medium text-yellow-800 dark:text-yellow-200">
                                Outstanding balance
                            </p>
                            <p className="text-2xl font-bold text-yellow-900 dark:text-yellow-100">
                                {formatCurrency(outstandingBalance)}
                            </p>
                        </div>
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => navigate('/invoices?status=SENT')}
                        >
                            View invoices
                            <ArrowRight size={14} className="ml-1"/>
                        </Button>
                    </CardContent>
                </Card>
            )}

            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                {summaryCards.map(({title, value, icon: Icon, description, colour}) => (
                    <Card key={title}>
                        <CardHeader className="flex flex-row items-center justify-between pb-2">
                            <CardTitle className="text-sm font-medium text-muted-foreground">
                                {title}
                            </CardTitle>
                            <Icon size={18} className={colour}/>
                        </CardHeader>
                        <CardContent>
                            <p className="text-2xl font-bold">{value}</p>
                            <p className="text-xs text-muted-foreground mt-1">{description}</p>
                        </CardContent>
                    </Card>
                ))}
            </div>

            <Card>
                <CardHeader className="flex flex-row items-center justify-between">
                    <CardTitle>Recent invoices</CardTitle>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => navigate('/invoices')}
                    >
                        View all
                        <ArrowRight size={14} className="ml-1"/>
                    </Button>
                </CardHeader>
                <CardContent>
                    {!allInvoices?.content.length ? (
                        <div className="text-center py-8 text-muted-foreground">
                            <FileText size={32} className="mx-auto mb-2 opacity-40"/>
                            <p>No invoices yet</p>
                            <Button
                                variant="link"
                                onClick={() => navigate('/invoices/new')}
                                className="mt-1"
                            >
                                Create your first invoice
                            </Button>
                        </div>
                    ) : (
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b text-muted-foreground text-left">
                                    <th className="pb-2 font-medium">Number</th>
                                    <th className="pb-2 font-medium">Client</th>
                                    <th className="pb-2 font-medium">Status</th>
                                    <th className="pb-2 font-medium text-right">Total</th>
                                    <th className="pb-2 font-medium text-right">Due</th>
                                </tr>
                            </thead>
                            <tbody>
                            {allInvoices.content.map((invoice: Invoice) => (
                                <tr
                                    key={invoice.id}
                                    className="border-b last:border-0 cursor-pointer hover:bg-muted/50 transition-colors"
                                    onClick={() => navigate(`/invoices/${invoice.id}`)}
                                >
                                    <td className="py-3 font-mono font-medium text-primary">
                                        {invoice.invoiceNumber}
                                    </td>
                                    <td className="py-3">{invoice.clientName}</td>
                                    <td className="py-3">
                                        <Badge variant={statusVariant[invoice.status]}>
                                            {invoice.status}
                                        </Badge>
                                    </td>
                                    <td className="py-3 text-right font-medium">
                                        {formatCurrency(invoice.total)}
                                    </td>
                                    <td className="py-3 text-right text-muted-foreground">
                                        {invoice.dueDate}
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}