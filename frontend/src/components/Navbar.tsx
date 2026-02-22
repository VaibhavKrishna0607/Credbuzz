'use client';

import React from 'react';
import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { useRouter } from 'next/navigation';

/**
 * ============================================
 * LEARNING NOTE: Navbar Component
 * ============================================
 * 
 * 'use client' is needed because we use:
 * - useAuth() hook (uses React state)
 * - useRouter() for navigation
 * - onClick handlers
 * 
 * Server components can't use these features.
 */

export default function Navbar() {
  const { user, logout } = useAuth();
  const router = useRouter();

  const handleLogout = () => {
    logout();
    router.push('/');
  };

  return (
    <nav className="bg-white shadow-sm border-b">
      <div className="container mx-auto px-4">
        <div className="flex justify-between items-center h-16">
          {/* Logo */}
          <Link href="/" className="text-2xl font-bold text-primary-600">
            CredBuzz
          </Link>

          {/* Navigation Links */}
          <div className="hidden md:flex items-center space-x-6">
            <Link 
              href="/tasks" 
              className="text-gray-600 hover:text-primary-600 transition"
            >
              Browse Tasks
            </Link>
            
            {user ? (
              <>
                <Link 
                  href="/create-task" 
                  className="text-gray-600 hover:text-primary-600 transition"
                >
                  Create Task
                </Link>
                <Link 
                  href="/my-tasks" 
                  className="text-gray-600 hover:text-primary-600 transition"
                >
                  My Tasks
                </Link>
                
                {/* User Menu */}
                <div className="flex items-center space-x-4">
                  {/* Credits Display */}
                  <span className="px-3 py-1 bg-primary-100 text-primary-700 rounded-full text-sm font-medium">
                    💰 {user.credits} credits
                  </span>
                  
                  {/* Profile Link */}
                  <Link 
                    href="/profile" 
                    className="text-gray-600 hover:text-primary-600 transition"
                  >
                    {user.name}
                  </Link>
                  
                  {/* Logout Button */}
                  <button
                    onClick={handleLogout}
                    className="text-gray-600 hover:text-red-600 transition"
                  >
                    Logout
                  </button>
                </div>
              </>
            ) : (
              <>
                <Link 
                  href="/login" 
                  className="text-gray-600 hover:text-primary-600 transition"
                >
                  Login
                </Link>
                <Link 
                  href="/register" 
                  className="btn-primary"
                >
                  Sign Up
                </Link>
              </>
            )}
          </div>

          {/* Mobile Menu Button */}
          {/* TODO: Implement mobile menu */}
          <button className="md:hidden p-2">
            <svg 
              className="w-6 h-6" 
              fill="none" 
              stroke="currentColor" 
              viewBox="0 0 24 24"
            >
              <path 
                strokeLinecap="round" 
                strokeLinejoin="round" 
                strokeWidth={2} 
                d="M4 6h16M4 12h16M4 18h16" 
              />
            </svg>
          </button>
        </div>
      </div>
    </nav>
  );
}
