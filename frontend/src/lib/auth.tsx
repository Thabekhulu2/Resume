/**
 * Auth context: wraps Supabase Auth session state and resolves the caller's
 * role (recruiter vs candidate) from row existence in the respective profile
 * table. See docs/specs/0008-auth-recruiter-candidate-login.md.
 */

import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import type { Session, User } from '@supabase/supabase-js';
import { supabase } from '@/data/supabase';

export type UserRole = 'recruiter' | 'candidate' | null;

interface AuthState {
  session: Session | null;
  user: User | null;
  role: UserRole;
  loading: boolean;
}

interface AuthContextValue extends AuthState {
  signIn: (email: string, password: string) => Promise<{ error: string | null }>;
  signUp: (email: string, password: string, fullName: string) => Promise<{ error: string | null }>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/** Row existence in `recruiters`/`candidates` is the role signal — mutually exclusive by design. */
export async function resolveRole(userId: string): Promise<UserRole> {
  const [{ data: recruiter }, { data: candidate }] = await Promise.all([
    supabase.from('recruiters').select('id').eq('id', userId).maybeSingle(),
    supabase.from('candidates').select('id').eq('id', userId).maybeSingle(),
  ]);
  if (recruiter) return 'recruiter';
  if (candidate) return 'candidate';
  return null;
}

/** Used from route `beforeLoad` guards, which run outside the React tree. */
export async function getSessionRole(): Promise<{ session: Session | null; role: UserRole }> {
  const { data } = await supabase.auth.getSession();
  if (!data.session) return { session: null, role: null };
  const role = await resolveRole(data.session.user.id);
  return { session: data.session, role };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({
    session: null,
    user: null,
    role: null,
    loading: true,
  });

  useEffect(() => {
    let mounted = true;

    async function apply(session: Session | null) {
      if (!session) {
        if (mounted) setState({ session: null, user: null, role: null, loading: false });
        return;
      }
      const role = await resolveRole(session.user.id);
      if (mounted) setState({ session, user: session.user, role, loading: false });
    }

    supabase.auth.getSession().then(({ data }) => apply(data.session));

    const { data: subscription } = supabase.auth.onAuthStateChange((_event, session) => {
      apply(session);
    });

    return () => {
      mounted = false;
      subscription.subscription.unsubscribe();
    };
  }, []);

  const signIn: AuthContextValue['signIn'] = async (email, password) => {
    const { error } = await supabase.auth.signInWithPassword({ email, password });
    return { error: error ? error.message : null };
  };

  const signUp: AuthContextValue['signUp'] = async (email, password, fullName) => {
    const { error } = await supabase.auth.signUp({
      email,
      password,
      options: { data: { signup_role: 'candidate', full_name: fullName } },
    });
    return { error: error ? error.message : null };
  };

  const signOut = async () => {
    await supabase.auth.signOut();
  };

  return (
    <AuthContext.Provider value={{ ...state, signIn, signUp, signOut }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
