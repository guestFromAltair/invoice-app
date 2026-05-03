import {useState} from 'react';
import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {
    Dialog, DialogContent, DialogHeader,
    DialogTitle, DialogTrigger,
} from './ui/dialog';
import {Button} from './ui/button';
import {Input} from './ui/input';
import {Label} from './ui/label';
import {toast} from 'sonner';
import {CreditCard} from 'lucide-react';
import type {PaymentRequest} from '@/types';

const paymentSchema = z.object({
    amount: z.number().positive('Amount must be greater than 0'),
    method: z.string().optional(),
    notes: z.string().optional()
});

type PaymentFormData = z.infer<typeof paymentSchema>;

interface PaymentDialogProps {
    invoiceId: string;
    remainingBalance: number;
    onSubmit: (data: PaymentRequest) => Promise<void>;
}

export function PaymentDialog({remainingBalance, onSubmit}: PaymentDialogProps) {
    const [open, setOpen] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const {register, handleSubmit, reset, formState: {errors}} =
        useForm<PaymentFormData>({
            resolver: zodResolver(paymentSchema),
            defaultValues: {amount: remainingBalance}
        });

    const handleFormSubmit = async (data: PaymentFormData) => {
        setIsSubmitting(true);
        try {
            await onSubmit({amount: data.amount, method: data.method, notes: data.notes});
            toast.success('Payment recorded');
            setOpen(false);
            reset();
        } catch {
            toast.error('Failed to record payment');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
                <Button variant="outline">
                    <CreditCard size={16} className="mr-2"/>
                    Record Payment
                </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-md">
                <DialogHeader>
                    <DialogTitle>Record Payment</DialogTitle>
                </DialogHeader>
                <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-4">
                    <div className="space-y-2">
                        <Label htmlFor="amount">Amount (EUR)</Label>
                        <Input
                            id="amount"
                            type="number"
                            step="0.01"
                            {...register('amount', {valueAsNumber: true})}
                        />
                        {errors.amount && (
                            <p className="text-sm text-destructive">{errors.amount.message}</p>
                        )}
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="method">Payment method</Label>
                        <Input
                            id="method"
                            placeholder="Bank transfer, credit card..."
                            {...register('method')}
                        />
                    </div>
                    <div className="space-y-2">
                        <Label htmlFor="notes">Notes</Label>
                        <Input
                            id="notes"
                            placeholder="Reference number, remarks..."
                            {...register('notes')}
                        />
                    </div>
                    <div className="flex gap-2 justify-end">
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => setOpen(false)}
                        >
                            Cancel
                        </Button>
                        <Button type="submit" disabled={isSubmitting}>
                            {isSubmitting ? 'Recording...' : 'Record Payment'}
                        </Button>
                    </div>
                </form>
            </DialogContent>
        </Dialog>
    );
}