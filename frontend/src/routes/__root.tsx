/**
 * Root Route - App Shell
 */

import { createRootRoute, Outlet, Link, useLocation, useNavigate } from '@tanstack/react-router';
import { TanStackRouterDevtools } from '@tanstack/router-devtools';
import { cn } from '@/lib/utils';
import { Home, UserPlus, History, LogOut, Briefcase } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/lib/auth';

export const Route = createRootRoute({
  component: RootComponent,
});

const PUBLIC_PATHS = ['/login', '/candidate/login', '/candidate/signup'];

function RootComponent() {
  const location = useLocation();
  const isPublicPage = PUBLIC_PATHS.includes(location.pathname);

  if (isPublicPage) {
    return (
      <div className="min-h-screen bg-background">
        <Outlet />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <div className="flex">
        <Sidebar />
        <main className="flex-1 min-w-0 p-6">
          <Outlet />
        </main>
      </div>
      {import.meta.env.DEV && (
        <TanStackRouterDevtools position="bottom-left" />
      )}
    </div>
  );
}

function Header() {
  const { user, role, signOut } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await signOut();
    navigate({ to: role === 'candidate' ? '/candidate/login' : '/login' });
  }

  return (
    <header className="h-16 border-b bg-card flex items-center gap-3 px-6">
      <img src="/brand/adapt-it-icon.png" alt="Adapt IT" className="h-8 w-8" />
      <h1 className="text-xl font-bold">Candidate Scoring</h1>
      {user && (
        <div className="ml-auto flex items-center gap-3 text-sm">
          <span className="text-muted-foreground">{user.email}</span>
          <Button variant="ghost" size="sm" onClick={handleLogout}>
            <LogOut className="h-4 w-4 mr-1" />
            Logout
          </Button>
        </div>
      )}
    </header>
  );
}

function Sidebar() {
  const location = useLocation();

  return (
    <aside className="w-64 border-r bg-card min-h-[calc(100vh-4rem)]">
      <nav className="p-4 space-y-2">
        <Link
          to="/"
          className={cn(
            'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors',
            location.pathname === '/'
              ? 'bg-primary text-primary-foreground'
              : 'hover:bg-muted'
          )}
        >
          <Home className="h-4 w-4" />
          Dashboard
        </Link>

        <Link
          to="/jobs"
          className={cn(
            'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors',
            location.pathname === '/jobs'
              ? 'bg-primary text-primary-foreground'
              : 'hover:bg-muted'
          )}
        >
          <Briefcase className="h-4 w-4" />
          Jobs
        </Link>

        <Link
          to="/candidates/upload"
          className={cn(
            'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors',
            location.pathname === '/candidates/upload'
              ? 'bg-primary text-primary-foreground'
              : 'hover:bg-muted'
          )}
        >
          <UserPlus className="h-4 w-4" />
          Score a Candidate
        </Link>

        <Link
          to="/candidates"
          className={cn(
            'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors',
            location.pathname === '/candidates'
              ? 'bg-primary text-primary-foreground'
              : 'hover:bg-muted'
          )}
        >
          <History className="h-4 w-4" />
          Candidate History
        </Link>
      </nav>
    </aside>
  );
}
