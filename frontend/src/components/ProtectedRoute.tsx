'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';

/**
 * ============================================
 * LEARNING NOTE: Protected Route in Next.js
 * ============================================
 * 
 * This is similar to your ProtectedRoute.jsx component.
 * It wraps pages that require authentication.
 * 
 * Usage:
 * <ProtectedRoute>
 *   <YourPage />
 * </ProtectedRoute>
 */

interface ProtectedRouteProps {
  children: React.ReactNode;
}

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    // If finished loading and no user, redirect to login
    if (!loading && !user) {
      router.push('/login');
    }
  }, [user, loading, router]);

  // Show loading while checking auth
  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">Loading...</div>
      </div>
    );
  }

  // If no user, don't render children (will redirect)
  if (!user) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">Redirecting to login...</div>
      </div>
    );
  }

  // User is authenticated, render children
  return <>{children}</>;
}
