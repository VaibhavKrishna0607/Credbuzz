import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import { AuthProvider } from '@/context/AuthContext';
import { Toaster } from 'react-hot-toast';
import Navbar from '@/components/Navbar';

/**
 * ============================================
 * LEARNING NOTE: Next.js App Router Layout
 * ============================================
 * 
 * In Next.js App Router, layout.tsx wraps all pages.
 * This is similar to your App.jsx that wrapped everything.
 * 
 * COMPARISON WITH REACT ROUTER:
 * -----------------------------------------
 * React Router (your old code):
 * <AuthProvider>
 *   <Router>
 *     <Routes>
 *       <Route path="/" element={<Home />} />
 *       ...
 *     </Routes>
 *   </Router>
 * </AuthProvider>
 * 
 * Next.js App Router:
 * - layout.tsx provides the wrapper (AuthProvider, etc.)
 * - Each page gets its own file (page.tsx)
 * - Routing is file-based, no need for <Routes>
 * -----------------------------------------
 */

const inter = Inter({ subsets: ['latin'] });

export const metadata: Metadata = {
  title: 'CredBuzz - Credit-based Task Marketplace',
  description: 'Exchange skills and services using credits',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className={inter.className}>
        {/* 
          AuthProvider wraps everything, just like in your React app.
          We'll create this context next.
        */}
        <AuthProvider>
          {/* Navbar appears on all pages */}
          <Navbar />
          
          {/* Page content renders here - Dark mode */}
          <main className="min-h-screen bg-slate-900 text-slate-100">
            {children}
          </main>
          
          {/* Toast notifications */}
          <Toaster 
            position="top-right" 
            toastOptions={{
              style: {
                background: '#1e293b',
                color: '#f1f5f9',
                border: '1px solid #334155',
              },
            }}
          />
        </AuthProvider>
      </body>
    </html>
  );
}
