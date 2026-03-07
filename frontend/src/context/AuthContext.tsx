'use client';

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import axios from 'axios';

/**
 * ============================================
 * LEARNING NOTE: Auth Context in Next.js
 * ============================================
 * 
 * This is similar to your AuthContext.jsx, adapted for Next.js + TypeScript.
 * 
 * KEY DIFFERENCES:
 * 1. 'use client' directive - tells Next.js this is a client component
 *    (contexts use React state, so they must be client components)
 * 
 * 2. TypeScript types - we define interfaces for User, Context, etc.
 * 
 * 3. Same logic as your React context!
 */

// ============================================
// TYPE DEFINITIONS
// ============================================

interface User {
  id: number;
  name: string;
  email: string;
  credits: number;
  avatar: string;
  bio: string;
  skills: string[];
  tasksCompleted?: number;
  tasksCreated?: number;
  joinedDate?: string;
  rating?: number;
  createdAt?: string;
}

interface AuthContextType {
  user: User | null;
  token: string | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<{ success: boolean; message?: string }>;
  register: (name: string, email: string, password: string) => Promise<{ success: boolean; message?: string }>;
  logout: () => void;
  updateUser: (userData: Partial<User>) => void;
  refreshUser: () => Promise<void>;
}

// ============================================
// CONTEXT CREATION
// ============================================

const AuthContext = createContext<AuthContextType | null>(null);

// API base URL from environment
const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';
axios.defaults.baseURL = API_URL;

// ============================================
// AUTH PROVIDER COMPONENT
// ============================================

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  // Load user from localStorage on mount
  useEffect(() => {
    const loadUser = async () => {
      try {
        const savedToken = localStorage.getItem('token');
        const savedUser = localStorage.getItem('user');
        
        if (savedToken) {
          setToken(savedToken);
          axios.defaults.headers.common['Authorization'] = `Bearer ${savedToken}`;
          
          if (savedUser) {
            setUser(JSON.parse(savedUser));
          }
          
          // Try to fetch fresh user data from API
          try {
            const res = await axios.get('/api/auth/me');
            if (res.data.success && res.data.user) {
              const userData = res.data.user;
              setUser(userData);
              localStorage.setItem('user', JSON.stringify(userData));
            }
          } catch (err: any) {
            console.error('Error fetching user:', err);
            // Token is expired or invalid — clear it so user is prompted to log in
            if (err.response?.status === 401 || err.response?.status === 403) {
              localStorage.removeItem('token');
              localStorage.removeItem('user');
              setToken(null);
              setUser(null);
              delete axios.defaults.headers.common['Authorization'];
            }
            // On other errors (network, etc.) keep the cached user
          }
        }
      } finally {
        setLoading(false);
      }
    };
    
    loadUser();
  }, []);

  /**
   * Login function
   * 
   * Same logic as your AuthContext login function
   */
  const login = async (email: string, password: string) => {
    try {
      const res = await axios.post('/api/auth/login', { email, password });
      
      const { token: newToken, user: userData } = res.data;
      
      setToken(newToken);
      setUser(userData);
      
      localStorage.setItem('token', newToken);
      localStorage.setItem('user', JSON.stringify(userData));
      
      axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
      
      return { success: true };
    } catch (err: any) {
      return { 
        success: false, 
        message: err.response?.data?.message || 'Login failed' 
      };
    }
  };

  /**
   * Register function
   */
  const register = async (name: string, email: string, password: string) => {
    try {
      const res = await axios.post('/api/auth/register', { name, email, password });
      
      const { token: newToken, user: userData } = res.data;
      
      setToken(newToken);
      setUser(userData);
      
      localStorage.setItem('token', newToken);
      localStorage.setItem('user', JSON.stringify(userData));
      
      axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`;
      
      return { success: true };
    } catch (err: any) {
      return { 
        success: false, 
        message: err.response?.data?.message || 'Registration failed' 
      };
    }
  };

  /**
   * Logout function
   */
  const logout = () => {
    setUser(null);
    setToken(null);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    delete axios.defaults.headers.common['Authorization'];
  };

  /**
   * Update user data (for profile updates)
   */
  const updateUser = (userData: Partial<User>) => {
    if (user) {
      const updatedUser = { ...user, ...userData };
      setUser(updatedUser);
      localStorage.setItem('user', JSON.stringify(updatedUser));
    }
  };

  /**
   * Refresh user data from API
   */
  const refreshUser = async () => {
    try {
      const res = await axios.get('/api/auth/me');
      if (res.data.success && res.data.user) {
        const userData = res.data.user;
        setUser(userData);
        localStorage.setItem('user', JSON.stringify(userData));
      }
    } catch (err: any) {
      console.error('Error refreshing user:', err);
      if (err.response?.status === 401 || err.response?.status === 403) {
        logout();
      }
    }
  };

  return (
    <AuthContext.Provider value={{ 
      user, 
      token, 
      loading, 
      login, 
      register, 
      logout,
      updateUser,
      refreshUser 
    }}>
      {children}
    </AuthContext.Provider>
  );
}

// ============================================
// CUSTOM HOOK
// ============================================

/**
 * Custom hook to use auth context
 * 
 * Usage: const { user, login, logout } = useAuth();
 */
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
