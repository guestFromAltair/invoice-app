import {Outlet, NavLink, useNavigate} from 'react-router-dom';
import {useDispatch, useSelector} from 'react-redux';
import {logout, selectCurrentUser} from '../store/authSlice';
import {
    LayoutDashboard,
    Users,
    FileText,
    LogOut,
    Menu,
    X,
} from 'lucide-react';
import {useState} from 'react';
import {Button} from './ui/button';
import {Separator} from './ui/separator';
import {cn} from '../lib/utils';
import {ThemeToggle} from './ThemeToggle';

const navItems = [
    {to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard},
    {to: '/clients', label: 'Clients', icon: Users},
    {to: '/invoices', label: 'Invoices', icon: FileText}
];

export default function Layout() {
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const user = useSelector(selectCurrentUser);
    const [sidebarOpen, setSidebarOpen] = useState(true);

    const handleLogout = () => {
        dispatch(logout());
        navigate('/login');
    };

    return (
        <div className="flex h-screen bg-background">
            <aside
                className={cn(
                    'flex flex-col border-r bg-card transition-all duration-200',
                    sidebarOpen ? 'w-56' : 'w-14'
                )}
            >
                <div className="flex items-center justify-between p-4">
                    {sidebarOpen && (
                        <span className="font-semibold text-primary">InvoiceApp</span>
                    )}
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setSidebarOpen(!sidebarOpen)}
                    >
                        {sidebarOpen ? <X size={18}/> : <Menu size={18}/>}
                    </Button>
                </div>

                <Separator/>

                <nav className="flex-1 p-2 space-y-1">
                    {navItems.map(({to, label, icon: Icon}) => (
                        <NavLink key={to} to={to}>
                            {({isActive}) => (
                                <div
                                    className={cn(
                                        'flex items-center gap-3 rounded-md px-3 py-2 text-sm transition-colors',
                                        isActive
                                            ? 'bg-primary text-primary-foreground'
                                            : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                                    )}
                                >
                                    <Icon size={18} className="shrink-0"/>
                                    {sidebarOpen && <span>{label}</span>}
                                </div>
                            )}
                        </NavLink>
                    ))}
                </nav>

                <Separator/>

                <div className="p-3 space-y-1">
                    <div className="flex items-center justify-between px-1">
                        {sidebarOpen && (
                            <p className="text-xs text-muted-foreground truncate">
                                {user.email}
                            </p>
                        )}
                        <ThemeToggle/>
                    </div>
                    <Button
                        variant="ghost"
                        size={sidebarOpen ? 'sm' : 'icon'}
                        onClick={handleLogout}
                        className="w-full justify-start gap-2 text-muted-foreground"
                    >
                        <LogOut size={16}/>
                        {sidebarOpen && 'Logout'}
                    </Button>
                </div>
            </aside>
            <main className="flex-1 overflow-auto">
                <div className="container mx-auto p-6 max-w-6xl">
                    <Outlet/>
                </div>
            </main>
        </div>
    );
}