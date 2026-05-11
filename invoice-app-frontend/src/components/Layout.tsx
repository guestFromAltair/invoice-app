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
    const [mobileOpen, setMobileOpen] = useState(false);

    const handleLogout = () => {
        dispatch(logout());
        navigate('/login');
    };

    return (
        <div className="flex h-screen bg-background overflow-hidden">
            {mobileOpen && (
                <div
                    className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm md:hidden transition-opacity"
                    onClick={() => setMobileOpen(false)}
                />
            )}
            <aside
                className={cn(
                    'flex flex-col border-r bg-card transition-all duration-200 z-50 h-full shrink-0',
                    'fixed inset-y-0 left-0 w-64 md:relative md:translate-x-0',
                    mobileOpen ? 'translate-x-0' : '-translate-x-full',
                    sidebarOpen ? 'md:w-56' : 'md:w-14'
                )}
            >
                <div className="flex items-center justify-between p-4 h-16">
                    <span className={cn("font-semibold text-primary block", !sidebarOpen && "md:hidden")}>
                        InvoiceApp
                    </span>
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setSidebarOpen(!sidebarOpen)}
                        className="hidden md:flex"
                    >
                        {sidebarOpen ? <X size={18}/> : <Menu size={18}/>}
                    </Button>
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setMobileOpen(false)}
                        className="md:hidden"
                    >
                        <X size={18}/>
                    </Button>
                </div>

                <Separator/>

                <nav className="flex-1 p-2 space-y-1 overflow-y-auto">
                    {navItems.map(({to, label, icon: Icon}) => (
                        <NavLink key={to} to={to} onClick={() => setMobileOpen(false)}>
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
                                    <span className={cn("block", !sidebarOpen && "md:hidden")}>
                                        {label}
                                    </span>
                                </div>
                            )}
                        </NavLink>
                    ))}
                </nav>

                <Separator/>

                <div className="p-3 space-y-2">
                    <div className="flex items-center justify-between px-1 gap-2">
                        {user?.email && (
                            <span className={cn(
                                "text-xs text-muted-foreground truncate block",
                                !sidebarOpen && "md:hidden"
                            )}>
                                {user.email}
                            </span>
                        )}
                        <ThemeToggle/>
                    </div>
                    <Button
                        variant="ghost"
                        size={sidebarOpen ? 'sm' : 'icon'}
                        onClick={handleLogout}
                        className={cn(
                            "w-full justify-start gap-2 text-muted-foreground",
                            !sidebarOpen && "md:justify-center md:px-0"
                        )}
                    >
                        <LogOut size={16}/>
                        <span className={cn("block", !sidebarOpen && "md:hidden")}>
                            Logout
                        </span>
                    </Button>
                </div>
            </aside>
            <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
                <header className="flex items-center justify-between px-4 h-16 border-b bg-card md:hidden shrink-0">
                    <span className="font-semibold text-primary">InvoiceApp</span>
                    <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setMobileOpen(true)}
                    >
                        <Menu size={20}/>
                    </Button>
                </header>
                <main className="flex-1 overflow-auto">
                    <div className="container mx-auto p-4 md:p-6 max-w-6xl">
                        <Outlet/>
                    </div>
                </main>
            </div>
        </div>
    );
}