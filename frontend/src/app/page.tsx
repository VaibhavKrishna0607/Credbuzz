import Link from 'next/link';

/**
 * ============================================
 * LEARNING NOTE: Next.js Page Component
 * ============================================
 * 
 * In Next.js App Router:
 * - page.tsx in a folder becomes a route
 * - /app/page.tsx -> "/" route
 * - /app/tasks/page.tsx -> "/tasks" route
 * - /app/tasks/[id]/page.tsx -> "/tasks/:id" route (dynamic)
 * 
 * No need for React Router! Routing is file-based.
 */

export default function Home() {
  return (
    <div className="min-h-screen">
      {/* Hero Section */}
      <section className="bg-gradient-to-r from-primary-600 to-primary-800 text-white py-20">
        <div className="container mx-auto px-4 text-center">
          <h1 className="text-5xl font-bold mb-6">
            Welcome to CredBuzz 2.0
          </h1>
          <p className="text-xl mb-8 text-primary-100">
            Exchange skills and services using credits. 
            Complete tasks to earn, create tasks to spend.
          </p>
          <div className="flex gap-4 justify-center">
            <Link 
              href="/tasks" 
              className="btn-primary text-lg px-8 py-3"
            >
              Browse Tasks
            </Link>
            <Link 
              href="/register" 
              className="btn bg-slate-800 text-primary-400 hover:bg-slate-700 border border-slate-600 text-lg px-8 py-3"
            >
              Get Started
            </Link>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="py-20">
        <div className="container mx-auto px-4">
          <h2 className="text-3xl font-bold text-center mb-12 text-slate-100">How It Works</h2>
          
          <div className="grid md:grid-cols-3 gap-8">
            {/* Feature 1 */}
            <div className="card text-center">
              <div className="text-4xl mb-4">📝</div>
              <h3 className="text-xl font-semibold mb-2 text-slate-100">Create Tasks</h3>
              <p className="text-slate-400">
                Post tasks you need help with. Set credit rewards for completion.
              </p>
            </div>
            
            {/* Feature 2 */}
            <div className="card text-center">
              <div className="text-4xl mb-4">🎯</div>
              <h3 className="text-xl font-semibold mb-2 text-slate-100">Complete Tasks</h3>
              <p className="text-slate-400">
                Browse available tasks and claim ones that match your skills.
              </p>
            </div>
            
            {/* Feature 3 */}
            <div className="card text-center">
              <div className="text-4xl mb-4">💰</div>
              <h3 className="text-xl font-semibold mb-2 text-slate-100">Earn Credits</h3>
              <p className="text-slate-400">
                Receive credits when your work is approved. Use them to create new tasks.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="bg-slate-800 py-20">
        <div className="container mx-auto px-4 text-center">
          <h2 className="text-3xl font-bold mb-4 text-slate-100">Ready to Start?</h2>
          <p className="text-slate-400 mb-8">
            Join our community and start exchanging skills today!
          </p>
          <Link 
            href="/register" 
            className="btn-primary text-lg px-8 py-3"
          >
            Create Account
          </Link>
        </div>
      </section>

      {/* TODO: Add more sections from your original Home.jsx */}
    </div>
  );
}
