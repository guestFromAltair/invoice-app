import {useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {
    useGetClientsQuery,
    useCreateClientMutation,
    useDeleteClientMutation,
} from '../store/apiSlice';
import {Button} from '../components/ui/button';
import {Input} from '../components/ui/input';
import {Label} from '../components/ui/label';
import {Card, CardContent, CardHeader, CardTitle} from '../components/ui/card';
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from '../components/ui/dialog';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '../components/ui/table';
import {Plus, Trash2, ExternalLink, Users, Mail, Phone, Hash} from 'lucide-react';
import {toast} from 'sonner';

const clientSchema = z.object({
    name: z.string().min(1, 'Name is required').max(255),
    email: z.email('Invalid email').optional().or(z.literal('')),
    phone: z.string().optional(),
    address: z.string().optional(),
    vatNumber: z.string().optional()
});

type ClientFormData = z.infer<typeof clientSchema>;

export default function ClientsPage() {
    const navigate = useNavigate();
    const [page, setPage] = useState(0);
    const [dialogOpen, setDialogOpen] = useState(false);

    const {data, isLoading, isFetching} = useGetClientsQuery({page, size: 20});
    const [createClient, {isLoading: isCreating}] = useCreateClientMutation();
    const [deleteClient] = useDeleteClientMutation();

    const {
        register,
        handleSubmit,
        reset,
        formState: {errors}
    } = useForm<ClientFormData>({resolver: zodResolver(clientSchema)});

    const onSubmit = async (data: ClientFormData) => {
        try {
            await createClient({
                name: data.name,
                email: data.email || undefined,
                phone: data.phone || undefined,
                address: data.address || undefined,
                vatNumber: data.vatNumber || undefined,
            }).unwrap();
            toast.success(`Client "${data.name}" created`);
            setDialogOpen(false);
            reset();
        } catch {
            toast.error('Failed to create client');
        }
    };

    const handleDelete = async (id: string, name: string) => {
        try {
            await deleteClient(id).unwrap();
            toast.success(`Client "${name}" deleted`);
        } catch {
            toast.error('Cannot delete client — they may have existing invoices');
        }
    };

    return (
        <div className="space-y-6 max-w-full overflow-hidden p-0.5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                    <h1 className="text-2xl sm:text-3xl font-bold tracking-tight">Clients</h1>
                    <p className="text-muted-foreground mt-1 text-sm">
                        {data?.totalElements ?? 0} clients total
                    </p>
                </div>

                <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
                    <DialogTrigger asChild>
                        <Button className="w-full sm:w-auto h-10">
                            <Plus size={16} className="mr-2"/>
                            New Client
                        </Button>
                    </DialogTrigger>
                    <DialogContent className="w-[92vw] max-w-md rounded-lg p-5 sm:p-6 gap-4">
                        <DialogHeader>
                            <DialogTitle className="text-lg">New client</DialogTitle>
                        </DialogHeader>
                        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 pt-1">
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
                                    <Label htmlFor="vatNumber" className="text-xs font-semibold">VAT number</Label>
                                    <Input id="vatNumber" {...register('vatNumber')} className="h-10"/>
                                </div>
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="address" className="text-xs font-semibold">Address</Label>
                                <Input id="address" {...register('address')} className="h-10"/>
                            </div>

                            <div className="flex gap-2 justify-end pt-2">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => {
                                        setDialogOpen(false);
                                        reset();
                                    }}
                                    className="h-10 text-xs sm:text-sm px-4"
                                >
                                    Cancel
                                </Button>
                                <Button type="submit" disabled={isCreating} className="h-10 text-xs sm:text-sm px-4">
                                    {isCreating ? 'Creating...' : 'Create client'}
                                </Button>
                            </div>
                        </form>
                    </DialogContent>
                </Dialog>
            </div>

            <Card className="border-muted/40">
                <CardHeader className="pb-3 px-4 sm:px-6">
                    <CardTitle className="flex items-center gap-2 text-base sm:text-lg">
                        <Users size={18} className="text-muted-foreground"/>
                        Client list
                    </CardTitle>
                </CardHeader>
                <CardContent className="px-2 sm:px-6">
                    <div className={isFetching ? 'opacity-60 transition-opacity' : ''}>
                        <div className="block md:hidden space-y-3">
                            {isLoading ? (
                                <div className="text-center py-8 text-muted-foreground text-sm">
                                    Loading clients...
                                </div>
                            ) : data?.content.length === 0 ? (
                                <div className="text-center py-8 text-muted-foreground text-sm">
                                    No clients yet — create your first one
                                </div>
                            ) : (
                                data?.content.map((client) => (
                                    <div
                                        key={client.id}
                                        className="p-4 border rounded-lg bg-card hover:bg-muted/5 transition-colors space-y-3.5"
                                    >
                                        <div className="flex items-start justify-between gap-2">
                                            <div className="min-w-0">
                                                <h3 className="font-semibold text-sm text-foreground truncate">
                                                    {client.name}
                                                </h3>
                                            </div>
                                            <div className="flex gap-1.5 shrink-0">
                                                <Button
                                                    variant="outline"
                                                    size="icon"
                                                    onClick={() => navigate(`/clients/${client.id}`)}
                                                    title="View client"
                                                    className="h-8 w-8 text-muted-foreground hover:text-foreground"
                                                >
                                                    <ExternalLink size={14}/>
                                                </Button>
                                                <Button
                                                    variant="outline"
                                                    size="icon"
                                                    onClick={() => handleDelete(client.id, client.name)}
                                                    title="Delete client"
                                                    className="h-8 w-8 text-destructive border-destructive/20 hover:bg-destructive/10 hover:text-destructive"
                                                >
                                                    <Trash2 size={14}/>
                                                </Button>
                                            </div>
                                        </div>

                                        <div className="space-y-1.5 pt-2 border-t text-xs text-muted-foreground">
                                            {client.email && (
                                                <div className="flex items-center gap-2">
                                                    <Mail size={13} className="shrink-0"/>
                                                    <span className="truncate">{client.email}</span>
                                                </div>
                                            )}
                                            {client.phone && (
                                                <div className="flex items-center gap-2">
                                                    <Phone size={13} className="shrink-0"/>
                                                    <span>{client.phone}</span>
                                                </div>
                                            )}
                                            {client.vatNumber && (
                                                <div className="flex items-center gap-2">
                                                    <Hash size={13} className="shrink-0"/>
                                                    <span
                                                        className="font-mono text-[11px]">VAT: {client.vatNumber}</span>
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>

                        <div className="hidden md:block overflow-x-auto">
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Name</TableHead>
                                        <TableHead>Email</TableHead>
                                        <TableHead>Phone</TableHead>
                                        <TableHead>VAT</TableHead>
                                        <TableHead className="w-24">Actions</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {isLoading ? (
                                        <TableRow>
                                            <TableCell
                                                colSpan={5}
                                                className="text-center py-8 text-muted-foreground"
                                            >
                                                Loading clients...
                                            </TableCell>
                                        </TableRow>
                                    ) : data?.content.length === 0 ? (
                                        <TableRow>
                                            <TableCell
                                                colSpan={5}
                                                className="text-center py-8 text-muted-foreground"
                                            >
                                                No clients yet — create your first one
                                            </TableCell>
                                        </TableRow>
                                    ) : (
                                        data?.content.map((client) => (
                                            <TableRow key={client.id} className="hover:bg-muted/50">
                                                <TableCell className="font-medium">
                                                    {client.name}
                                                </TableCell>
                                                <TableCell className="text-muted-foreground">
                                                    {client.email ?? '—'}
                                                </TableCell>
                                                <TableCell className="text-muted-foreground">
                                                    {client.phone ?? '—'}
                                                </TableCell>
                                                <TableCell className="text-muted-foreground font-mono text-xs">
                                                    {client.vatNumber ?? '—'}
                                                </TableCell>
                                                <TableCell>
                                                    <div className="flex gap-1">
                                                        <Button
                                                            variant="ghost"
                                                            size="icon"
                                                            onClick={() => navigate(`/clients/${client.id}`)}
                                                            title="View client"
                                                        >
                                                            <ExternalLink size={15}/>
                                                        </Button>
                                                        <Button
                                                            variant="ghost"
                                                            size="icon"
                                                            onClick={() => handleDelete(client.id, client.name)}
                                                            title="Delete client"
                                                            className="text-destructive hover:text-destructive"
                                                        >
                                                            <Trash2 size={15}/>
                                                        </Button>
                                                    </div>
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