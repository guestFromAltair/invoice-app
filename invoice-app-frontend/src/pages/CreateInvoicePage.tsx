import {useEffect, useMemo} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useForm, useFieldArray, useWatch, type Resolver} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {
    useGetClientsQuery,
    useCreateInvoiceMutation
} from '../store/apiSlice';
import {Button} from '../components/ui/button';
import {Input} from '../components/ui/input';
import {Label} from '../components/ui/label';
import {Card, CardContent, CardHeader, CardTitle} from '../components/ui/card';
import {Separator} from '../components/ui/separator';
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue
} from '../components/ui/select';
import {Textarea} from '../components/ui/textarea';
import {ArrowLeft, Plus, Trash2} from 'lucide-react';
import {toast} from 'sonner';
import {calcLineTotal} from "@/utils/calculations.ts";

const lineItemSchema = z.object({
    description: z.string().min(1, 'Description is required'),
    quantity: z.coerce.number().positive('Must be positive'),
    unitPrice: z.coerce.number().positive('Must be positive'),
    discountPct: z.coerce.number().min(0).max(1).default(0),
    position: z.coerce.number().int().default(0)
});

const invoiceSchema = z.object({
    clientId: z.uuid('Please select a client'),
    issueDate: z.string().min(1, 'Issue date is required'),
    dueDate: z.string().min(1, 'Due date is required'),
    taxRate: z.coerce.number().min(0).max(1).default(0),
    notes: z.string().optional(),
    lineItems: z.array(lineItemSchema).min(1)
});

type InvoiceFormData = z.infer<typeof invoiceSchema>;

const formatCurrency = (amount: number): string =>
    new Intl.NumberFormat('fr-FR', {style: 'currency', currency: 'EUR'})
        .format(amount);

export default function CreateInvoicePage() {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const preselectedClientId = searchParams.get('clientId') ?? '';

    const {data: clients} = useGetClientsQuery({size: 100});

    const [createInvoice, {isLoading}] = useCreateInvoiceMutation();

    const {today, thirtyDaysFromNow} = useMemo(() => {
        const now = new Date();
        const future = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000);

        return {
            today: now.toISOString().split('T')[0],
            thirtyDaysFromNow: future.toISOString().split('T')[0]
        };
    }, [])

    const {
        register,
        control,
        handleSubmit,
        setValue,
        formState: {errors}
    } = useForm<InvoiceFormData>({
        resolver: zodResolver(invoiceSchema) as Resolver<InvoiceFormData>,
        defaultValues: {
            clientId: preselectedClientId,
            issueDate: today,
            dueDate: thirtyDaysFromNow,
            taxRate: 0.20,
            lineItems: [
                {description: '', quantity: 1, unitPrice: 0, discountPct: 0, position: 0}
            ]
        }
    });

    const {fields, append, remove} = useFieldArray({
        control,
        name: 'lineItems'
    });

    const watchedLineItems = useWatch({control, name: 'lineItems'});
    const watchedTaxRate = useWatch({control, name: 'taxRate'});

    const subtotal = watchedLineItems?.reduce((sum, item) =>
        sum + calcLineTotal(Number(item.quantity), Number(item.unitPrice), Number(item.discountPct)), 0) ?? 0;

    const taxAmount = subtotal * (Number(watchedTaxRate) || 0);
    const total = subtotal + taxAmount;

    useEffect(() => {
        fields.forEach((_, index) => {
            setValue(`lineItems.${index}.position`, index);
        });
    }, [fields, setValue]);

    const onSubmit = async (data: InvoiceFormData) => {
        try {
            const created = await createInvoice({
                clientId: data.clientId,
                issueDate: data.issueDate,
                dueDate: data.dueDate,
                taxRate: data.taxRate,
                notes: data.notes,
                lineItems: data.lineItems.map((li, index) => ({
                    description: li.description,
                    quantity: li.quantity,
                    unitPrice: li.unitPrice,
                    discountPct: li.discountPct ?? 0,
                    position: index
                }))
            }).unwrap();
            toast.success(`Invoice ${created.invoiceNumber} created`);
            navigate(`/invoices/${created.id}`);
        } catch {
            toast.error('Failed to create invoice');
        }
    };

    return (
        <div className="space-y-6 max-w-full overflow-hidden p-0.5">
            <div className="flex items-center gap-3 sm:gap-4">
                <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => navigate('/invoices')}
                    className="shrink-0"
                >
                    <ArrowLeft size={18}/>
                </Button>
                <div>
                    <h1 className="text-2xl sm:text-3xl font-bold tracking-tight">New Invoice</h1>
                    <p className="text-muted-foreground text-sm mt-0.5 sm:mt-1">
                        Create a new invoice draft
                    </p>
                </div>
            </div>

            <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-lg">Details</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            <div className="space-y-2">
                                <Label>Client *</Label>
                                <Select
                                    defaultValue={preselectedClientId}
                                    onValueChange={(value) => setValue('clientId', value)}
                                >
                                    <SelectTrigger className="h-10">
                                        <SelectValue placeholder="Select a client"/>
                                    </SelectTrigger>
                                    <SelectContent>
                                        {clients?.content.map((client) => (
                                            <SelectItem key={client.id} value={client.id}>
                                                {client.name}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                                {errors.clientId && (
                                    <p className="text-xs text-destructive">
                                        {errors.clientId.message}
                                    </p>
                                )}
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <div className="space-y-2">
                                    <Label htmlFor="issueDate">Issue date *</Label>
                                    <Input
                                        id="issueDate"
                                        type="date"
                                        className="h-10"
                                        {...register('issueDate')}
                                    />
                                    {errors.issueDate && (
                                        <p className="text-xs text-destructive">
                                            {errors.issueDate.message}
                                        </p>
                                    )}
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="dueDate">Due date *</Label>
                                    <Input
                                        id="dueDate"
                                        type="date"
                                        className="h-10"
                                        {...register('dueDate')}
                                    />
                                    {errors.dueDate && (
                                        <p className="text-xs text-destructive">
                                            {errors.dueDate.message}
                                        </p>
                                    )}
                                </div>
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="taxRate">Tax rate</Label>
                                <Select
                                    defaultValue="0.20"
                                    onValueChange={(v) =>
                                        setValue('taxRate', parseFloat(v))
                                    }
                                >
                                    <SelectTrigger className="h-10">
                                        <SelectValue/>
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="0">0% — No tax</SelectItem>
                                        <SelectItem value="0.055">5.5% — Reduced</SelectItem>
                                        <SelectItem value="0.10">10% — Intermediate</SelectItem>
                                        <SelectItem value="0.20">20% — Standard VAT</SelectItem>
                                    </SelectContent>
                                </Select>
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="notes">Notes</Label>
                                <Textarea
                                    id="notes"
                                    placeholder="Payment terms, bank details, additional information..."
                                    rows={3}
                                    {...register('notes')}
                                />
                            </div>
                        </CardContent>
                    </Card>
                    <Card className="flex flex-col justify-between">
                        <div>
                            <CardHeader>
                                <CardTitle className="text-lg">Summary</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-4">
                                <div className="flex justify-between text-sm">
                                    <span className="text-muted-foreground">Subtotal</span>
                                    <span className="font-medium">{formatCurrency(subtotal)}</span>
                                </div>
                                <div className="flex justify-between text-sm">
                                    <span className="text-muted-foreground">
                                        Tax ({((Number(watchedTaxRate) || 0) * 100).toFixed(0)}%)
                                    </span>
                                    <span className="font-medium">{formatCurrency(taxAmount)}</span>
                                </div>
                                <Separator/>
                                <div className="flex justify-between items-baseline">
                                    <span className="font-semibold text-sm">Total</span>
                                    <span className="font-bold text-xl text-primary">
                                        {formatCurrency(total)}
                                    </span>
                                </div>
                            </CardContent>
                        </div>
                        <div className="p-6 pt-0">
                            <p className="text-xs text-muted-foreground">
                                {fields.length} line item{fields.length !== 1 ? 's' : ''}
                            </p>
                        </div>
                    </Card>
                </div>
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-4">
                        <CardTitle className="text-lg">Line Items</CardTitle>
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() =>
                                append({
                                    description: '',
                                    quantity: 1,
                                    unitPrice: 0,
                                    discountPct: 0,
                                    position: fields.length
                                })
                            }
                        >
                            <Plus size={15} className="mr-1.5"/>
                            Add line
                        </Button>
                    </CardHeader>
                    <CardContent className="space-y-4">
                        {errors.lineItems?.root && (
                            <p className="text-sm text-destructive">
                                {errors.lineItems.root.message}
                            </p>
                        )}
                        <div
                            className="hidden md:grid grid-cols-12 gap-2 text-xs text-muted-foreground px-1 font-medium">
                            <div className="col-span-5">Description</div>
                            <div className="col-span-2">Quantity</div>
                            <div className="col-span-2">Unit price</div>
                            <div className="col-span-1">Discount %</div>
                            <div className="col-span-1 text-right">Total</div>
                            <div className="col-span-1"/>
                        </div>
                        <div className="space-y-4 md:space-y-3">
                            {fields.map((field, index) => {
                                const qty = Number(watchedLineItems?.[index]?.quantity ?? 0);
                                const price = Number(watchedLineItems?.[index]?.unitPrice ?? 0);
                                const discount = Number(watchedLineItems?.[index]?.discountPct ?? 0);
                                const lineTotal = calcLineTotal(qty, price, discount);

                                return (
                                    <div
                                        key={field.id}
                                        className="flex flex-col md:grid md:grid-cols-12 gap-3 md:gap-2 p-4 md:p-0 border md:border-0 rounded-lg md:rounded-none bg-muted/20 md:bg-transparent"
                                    >
                                        <div className="col-span-5 space-y-1.5 md:space-y-0">
                                            <Label className="text-xs text-muted-foreground md:hidden">Description</Label>
                                            <Input
                                                placeholder="Description..."
                                                className="h-10 text-sm bg-background"
                                                {...register(`lineItems.${index}.description`)}
                                            />
                                            {errors.lineItems?.[index]?.description && (
                                                <p className="text-xs text-destructive mt-1">
                                                    {errors.lineItems[index]?.description?.message}
                                                </p>
                                            )}
                                        </div>
                                        <div className="grid grid-cols-3 gap-2 md:contents">
                                            <div className="col-span-2 space-y-1.5 md:space-y-0">
                                                <Label className="text-xs text-muted-foreground md:hidden">Qty</Label>
                                                <Input
                                                    type="number"
                                                    step="0.01"
                                                    min="0"
                                                    placeholder="1"
                                                    className="h-10 text-sm bg-background"
                                                    {...register(`lineItems.${index}.quantity`, {
                                                        valueAsNumber: true
                                                    })}
                                                />
                                            </div>
                                            <div className="col-span-2 space-y-1.5 md:space-y-0">
                                                <Label className="text-xs text-muted-foreground md:hidden">Unit price</Label>
                                                <Input
                                                    type="number"
                                                    step="0.01"
                                                    min="0"
                                                    placeholder="0.00"
                                                    className="h-10 text-sm bg-background"
                                                    {...register(`lineItems.${index}.unitPrice`, {
                                                        valueAsNumber: true
                                                    })}
                                                />
                                            </div>
                                            <div className="col-span-1 space-y-1.5 md:space-y-0">
                                                <Label className="text-xs text-muted-foreground md:hidden">Disc. %</Label>
                                                <Input
                                                    type="number"
                                                    step="1"
                                                    min="0"
                                                    max="100"
                                                    placeholder="0"
                                                    className="h-10 text-sm bg-background"
                                                    onChange={(e) => {
                                                        const pct = parseFloat(e.target.value) || 0;
                                                        setValue(
                                                            `lineItems.${index}.discountPct`,
                                                            pct / 100
                                                        );
                                                    }}
                                                    defaultValue={
                                                        (field.discountPct ?? 0) * 100
                                                    }
                                                />
                                            </div>
                                        </div>
                                        <div className="flex items-center justify-between md:contents pt-2 md:pt-0 border-t md:border-0 mt-1 md:mt-0">
                                            <div className="col-span-1 flex flex-col md:items-end justify-center md:pt-2">
                                                <span className="text-xs text-muted-foreground md:hidden">Total</span>
                                                <span className="text-sm font-semibold text-primary break-all">
                                                  {formatCurrency(lineTotal)}
                                                </span>
                                            </div>
                                            <div className="col-span-1 flex items-center justify-end md:justify-center md:pt-1">
                                                <Button
                                                    type="button"
                                                    variant="ghost"
                                                    size="icon"
                                                    onClick={() => remove(index)}
                                                    disabled={fields.length === 1}
                                                    title="Remove line"
                                                    className="text-muted-foreground hover:text-destructive h-9 w-9 hover:bg-destructive/10"
                                                >
                                                    <Trash2 size={16}/>
                                                </Button>
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </CardContent>
                </Card>
                <div className="flex flex-col-reverse sm:flex-row gap-3 sm:justify-end pt-2">
                    <Button
                        type="button"
                        variant="outline"
                        onClick={() => navigate('/invoices')}
                        className="w-full sm:w-auto h-11 sm:h-10"
                    >
                        Cancel
                    </Button>
                    <Button
                        type="submit"
                        disabled={isLoading}
                        className="w-full sm:w-auto h-11 sm:h-10"
                    >
                        {isLoading ? 'Creating...' : 'Create Invoice'}
                    </Button>
                </div>
            </form>
        </div>
    );
}