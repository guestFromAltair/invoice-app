import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {useDispatch} from 'react-redux';
import {useNavigate, Link} from 'react-router-dom';
import {useRegisterMutation} from '../store/apiSlice';
import {setCredentials} from '../store/authSlice';
import {Button} from '../components/ui/button';
import {Input} from '../components/ui/input';
import {Label} from '../components/ui/label';
import {
    Card,
    CardContent,
    CardHeader,
    CardTitle,
    CardDescription,
} from '../components/ui/card';
import {toast} from 'sonner';

const registerSchema = z
    .object({
        email: z.email('Please enter a valid email'),
        password: z.string().min(8, 'Password must be at least 8 characters'),
        confirmPassword: z.string()
    })
    .refine((data) => data.password === data.confirmPassword, {
        message: 'Passwords do not match',
        path: ['confirmPassword']
    });

type RegisterFormData = z.infer<typeof registerSchema>;

export default function RegisterPage() {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [register, {isLoading}] = useRegisterMutation();

    const {
        register: registerField,
        handleSubmit,
        formState: {errors}
    } = useForm<RegisterFormData>({
        resolver: zodResolver(registerSchema)
    });

    const onSubmit = async (data: RegisterFormData) => {
        try {
            const result = await register({
                email: data.email,
                password: data.password
            }).unwrap();
            dispatch(setCredentials(result));
            toast.success('Account created successfully');
            navigate('/dashboard');
        } catch {
            toast.error('Registration failed — this email may already be in use');
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-background p-4">
            <Card className="w-full max-w-sm">
                <CardHeader className="space-y-1">
                    <CardTitle className="text-2xl font-bold">Create account</CardTitle>
                    <CardDescription>
                        Enter your details to get started
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                        <div className="space-y-2">
                            <Label htmlFor="email">Email</Label>
                            <Input
                                id="email"
                                type="email"
                                placeholder="you@example.com"
                                {...registerField('email')}
                            />
                            {errors.email && (
                                <p className="text-sm text-destructive">
                                    {errors.email.message}
                                </p>
                            )}
                        </div>

                        <div className="space-y-2">
                            <Label htmlFor="password">Password</Label>
                            <Input
                                id="password"
                                type="password"
                                {...registerField('password')}
                            />
                            {errors.password && (
                                <p className="text-sm text-destructive">
                                    {errors.password.message}
                                </p>
                            )}
                        </div>

                        <div className="space-y-2">
                            <Label htmlFor="confirmPassword">Confirm password</Label>
                            <Input
                                id="confirmPassword"
                                type="password"
                                {...registerField('confirmPassword')}
                            />
                            {errors.confirmPassword && (
                                <p className="text-sm text-destructive">
                                    {errors.confirmPassword.message}
                                </p>
                            )}
                        </div>

                        <Button type="submit" className="w-full" disabled={isLoading}>
                            {isLoading ? 'Creating account...' : 'Create account'}
                        </Button>

                        <p className="text-sm text-center text-muted-foreground">
                            Already have an account?{' '}
                            <Link to="/login" className="text-primary hover:underline">
                                Sign in
                            </Link>
                        </p>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}