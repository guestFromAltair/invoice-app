import {useNavigate} from 'react-router-dom';
import {useDispatch, useSelector} from 'react-redux';
import {selectIsAuthenticated, setCredentials} from '../store/authSlice';
import {Button} from '../components/ui/button';
import {FileText, Shield, TrendingUp, Zap} from 'lucide-react';
import {useLoginMutation} from "@/store/apiSlice.ts";
import {toast} from "sonner";

export default function LandingPage() {
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const isAuthenticated = useSelector(selectIsAuthenticated);
    const [login, {isLoading}] = useLoginMutation();

    const handleDemoLogin = async () => {
        try {
            const result = await login({email: 'demo@invoiceapp.com', password: 'password'}).unwrap();
            dispatch(setCredentials(result));
            navigate('/dashboard');
            toast.success('Welcome to the demo sandbox!');
        } catch {
            toast.error('Could not log into demo account');
        }
    };

    return (
        <div className="min-h-screen bg-background">
            <nav className="border-b">
                <div className="container mx-auto px-6 py-4 flex items-center justify-between">
                    <span className="font-bold text-xl text-primary">InvoiceApp</span>
                    <div className="flex gap-3">
                        {isAuthenticated ? (
                            <Button onClick={() => navigate('/dashboard')}>
                                Go to dashboard
                            </Button>
                        ) : (
                            <>
                                <Button variant="ghost" onClick={() => navigate('/login')}>
                                    Sign in
                                </Button>
                                <Button onClick={() => navigate('/register')}>
                                    Get started
                                </Button>
                            </>
                        )}
                    </div>
                </div>
            </nav>
            <section className="container mx-auto px-6 py-24 text-center max-w-3xl">
                <h1 className="text-5xl font-bold tracking-tight mb-6">
                    Professional invoicing
                    <span className="text-primary"> built for accuracy</span>
                </h1>
                <p className="text-xl text-muted-foreground mb-10">
                    Create invoices, track payments, and manage clients —
                    with financial-grade precision and real-time notifications.
                </p>
                <div className="flex gap-4 justify-center items-center flex-wrap">
                    {isAuthenticated ? (
                        <Button size="lg" onClick={() => navigate('/dashboard')}>
                            Go to dashboard
                        </Button>
                    ) : (
                        <div className="flex flex-col sm:flex-row gap-3 justify-center items-stretch sm:items-center max-w-xs mx-auto sm:max-w-none">
                            <Button size="lg" onClick={() => navigate('/register')}>
                                Start for free
                            </Button>
                            <Button
                                size="lg"
                                variant="outline"
                                onClick={() => navigate('/login')}
                            >
                                Sign in
                            </Button>
                            <Button
                                size="lg"
                                variant="secondary"
                                className="border-2 border-dashed border-primary/40 bg-primary/5 hover:bg-primary/10 font-semibold"
                                onClick={handleDemoLogin}
                                disabled={isLoading}
                            >
                                {isLoading ? 'Exploring...' : '⚡ Quick Demo'}
                            </Button>
                        </div>
                    )}
                </div>
            </section>
            <section className="container mx-auto px-6 py-16 max-w-5xl">
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8">
                    {[
                        {
                            icon: FileText,
                            title: 'Smart invoicing',
                            description: 'Full invoice lifecycle — draft, send, track, and collect with automatic overdue detection.'
                        },
                        {
                            icon: Shield,
                            title: 'Payment protection',
                            description: 'Idempotent payment processing prevents duplicate charges even on network failures.'
                        },
                        {
                            icon: TrendingUp,
                            title: 'Financial insights',
                            description: 'Real-time revenue dashboard with outstanding balance tracking and payment history.'
                        },
                        {
                            icon: Zap,
                            title: 'Live notifications',
                            description: 'Server-sent events notify you instantly when invoice status changes.'
                        }
                    ].map(({icon: Icon, title, description}) => (
                        <div key={title} className="space-y-3">
                            <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                                <Icon size={20} className="text-primary"/>
                            </div>
                            <h3 className="font-semibold">{title}</h3>
                            <p className="text-sm text-muted-foreground">{description}</p>
                        </div>
                    ))}
                </div>
            </section>
        </div>
    );
}