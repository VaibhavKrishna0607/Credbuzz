/** @type {import('next').NextConfig} */
const nextConfig = {
  /**
   * ============================================
   * LEARNING NOTE: Next.js Configuration
   * ============================================
   * 
   * This file configures Next.js behavior.
   * 
   * COMPARISON WITH VITE:
   * - vite.config.js -> next.config.js
   * - Different options but same purpose
   */
  
  // Enable React Strict Mode for better debugging
  reactStrictMode: true,
  
  // Image optimization domains
  images: {
    domains: ['localhost', 'lh3.googleusercontent.com'],
  },
  
  // Environment variables accessible in browser
  // (Next.js requires NEXT_PUBLIC_ prefix for client-side env vars)
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
    NEXT_PUBLIC_GOOGLE_CLIENT_ID: process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID,
  },
};

module.exports = nextConfig;
