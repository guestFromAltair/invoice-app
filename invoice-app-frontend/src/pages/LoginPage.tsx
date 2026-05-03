import {useForm} from 'react-hook-form';
import {zodResolver} from '@hookform/resolvers/zod';
import {z} from 'zod';
import {useDispatch} from 'react-redux';
import {useNavigate, Link} from 'react-router-dom';
import {useLoginMutation} from '../store/apiSlice';
import {setCredentials} from '../store/authSlice';
import {Button} from '../components/ui/button';
import {Input} from '../components/ui/input';
import {Label} from '../components/ui/label';
import {Card, CardContent, CardHeader, CardTitle, CardDescription} from '../components/ui/card';
import {toast} from 'sonner';

const loginSchema = z.object({
    email: z.email('Please enter a valid email'),
    password: z.string().min(8, 'Password must be at least 8 characters')
});

type LoginFormData = z.infer<typeof loginSchema>;

export default function LoginPage() {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [login, {isLoading}] = useLoginMutation();

    const {
        register,
        handleSubmit,
        formState: {errors}
    } = useForm<LoginFormData>({
        resolver: zodResolver(loginSchema)
    });

    const onSubmit = async (data: LoginFormData) => {
        try {
            const result = await login(data).unwrap();
            dispatch(setCredentials(result));
            navigate('/dashboard');
        } catch {
            toast.error('Invalid email or password');
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-background p-4">
            <Card className="w-full max-w-sm">
                <CardHeader className="space-y-1">
                    <CardTitle className="text-2xl font-bold">Sign in</CardTitle>
                    <CardDescription>
                        Enter your email and password to access your account
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
                                {...register('email')}
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
                                {...register('password')}
                            />
                            {errors.password && (
                                <p className="text-sm text-destructive">
                                    {errors.password.message}
                                </p>
                            )}
                        </div>

                        <Button type="submit" className="w-full" disabled={isLoading}>
                            {isLoading ? 'Signing in...' : 'Sign in'}
                        </Button>

                        <p className="text-sm text-center text-muted-foreground">
                            No account?{' '}
                            <Link to="/register" className="text-primary hover:underline">
                                Register
                            </Link>
                        </p>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}