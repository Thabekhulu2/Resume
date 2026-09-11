/**
 * Root Route - App Shell
 */

import { createRootRoute, Outlet, Link, useLocation, useNavigate } from '@tanstack/react-router';
import { TanStackRouterDevtools } from '@tanstack/router-devtools';
import { cn } from '@/lib/utils';
import {
  Home,
  UserPlus,
  History,
  LogOut,
  Briefcase,
  Inbox,
  Star,
  Bell,
  MessageSquare,
  UserCircle,
  Search,
  Settings,
  Calendar,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/lib/auth';

export const Route = createRootRoute({
  component: RootComponent,
});

const PUBLIC_PATHS = ['/login', '/candidate/signup'];

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
        <RoleAwareSidebar />
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

/** Candidates get their own portal nav, recruiters get the internal nav. */
function RoleAwareSidebar() {
  const { role } = useAuth();
  return role === 'candidate' ? <CandidateSidebar /> : <Sidebar />;
}

function CandidateNavLink({
  to,
  icon,
  children,
  badge,
}: {
  to: string;
  icon: React.ReactNode;
  children: React.ReactNode;
  badge?: number;
}) {
  const location = useLocation();
  const active = location.pathname === to;
  return (
    <Link
      to={to}
      className={cn(
        'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors',
        active ? 'bg-primary text-primary-foreground' : 'hover:bg-muted'
      )}
    >
      {icon}
      <span className="flex-1">{children}</span>
      {typeof badge === 'number' && badge > 0 && (
        <span
          className={cn(
            'text-xs rounded-full px-1.5 py-0.5 min-w-[1.25rem] text-center',
            active ? 'bg-primary-foreground text-primary' : 'bg-primary text-primary-foreground'
          )}
        >
          {badge}
        </span>
      )}
    </Link>
  );
}

function CandidateSidebar() {
  return (
    <aside className="w-64 border-r bg-card min-h-[calc(100vh-4rem)]">
      <nav className="p-4 space-y-2">
        <CandidateNavLink to="/dashboard" icon={<Home className="h-4 w-4" />}>
          Dashboard
        </CandidateNavLink>
        <CandidateNavLink to="/apply" icon={<Briefcase className="h-4 w-4" />}>
          Apply for Jobs
        </CandidateNavLink>
        <CandidateNavLink to="/my-applications" icon={<Inbox className="h-4 w-4" />}>
          My Applications
        </CandidateNavLink>
        <CandidateNavLink to="/saved-jobs" icon={<Star className="h-4 w-4" />}>
          Saved Jobs
        </CandidateNavLink>
        <CandidateNavLink to="/notifications" icon={<Bell className="h-4 w-4" />} badge={3}>
          Notifications
        </CandidateNavLink>
        <CandidateNavLink to="/messages" icon={<MessageSquare className="h-4 w-4" />} badge={2}>
          Messages
        </CandidateNavLink>

        <div className="text-xs uppercase text-muted-foreground px-3 pt-4 pb-1">My account</div>

        <CandidateNavLink to="/profile" icon={<UserCircle className="h-4 w-4" />}>
          My Profile
        </CandidateNavLink>
        <CandidateNavLink to="/job-alerts" icon={<Search className="h-4 w-4" />}>
          Job Alerts
        </CandidateNavLink>
        <CandidateNavLink to="/settings" icon={<Settings className="h-4 w-4" />}>
          Settings
        </CandidateNavLink>
      </nav>
    </aside>
  );
}

function Header() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await signOut();
    navigate({ to: '/login' });
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
          to="/applications"
          className={cn(
            'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors',
            location.pathname === '/applications'
              ? 'bg-primary text-primary-foreground'
              : 'hover:bg-muted'
          )}
        >
          <Inbox className="h-4 w-4" />
          Applications
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

        <Link
          to="/interviews"
          className={cn(
            'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors',
            location.pathname === '/interviews'
              ? 'bg-primary text-primary-foreground'
              : 'hover:bg-muted'
          )}
        >
          <Calendar className="h-4 w-4" />
          Scheduled Interviews
        </Link>
      </nav>
    </aside>
  );
}
