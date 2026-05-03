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
import {Plus, Trash2, ExternalLink, Users} from 'lucide-react';
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
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold tracking-tight">Clients</h1>
                    <p className="text-muted-foreground mt-1">
                        {data?.totalElements ?? 0} clients total
                    </p>
                </div>

                <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
                    <DialogTrigger asChild>
                        <Button>
                            <Plus size={16} className="mr-2"/>
                            New Client
                        </Button>
                    </DialogTrigger>
                    <DialogContent className="sm:max-w-md">
                        <DialogHeader>
                            <DialogTitle>New client</DialogTitle>
                        </DialogHeader>
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

                            <div className="flex gap-2 justify-end">
                                <Button
                                    type="button"
                                    variant="outline"
                                    onClick={() => {
                                        setDialogOpen(false);
                                        reset();
                                    }}
                                >
                                    Cancel
                                </Button>
                                <Button type="submit" disabled={isCreating}>
                                    {isCreating ? 'Creating...' : 'Create client'}
                                </Button>
                            </div>
                        </form>
                    </DialogContent>
                </Dialog>
            </div>

            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <Users size={20}/>
                        Client list
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <div className={isFetching ? 'opacity-60 transition-opacity' : ''}>
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
                                        <TableRow key={client.id}>
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