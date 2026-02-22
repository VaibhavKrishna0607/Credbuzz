'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import axios from 'axios';

/**
 * ============================================
 * LEARNING NOTE: Tasks Marketplace Page
 * ============================================
 * 
 * This is your TaskMarketplace.jsx adapted for Next.js.
 * It shows available tasks that users can claim.
 */

interface Task {
  id: number;
  title: string;
  description: string;
  credits: number;
  skills: string[];
  deadline: string;
  status: string;
  estimatedHours?: number;
  category: string;
  poster: {
    id: number;
    name: string;
    avatar?: string;
    credits?: number;
  };
}

export default function TasksPage() {
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  // Filters
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');

  useEffect(() => {
    fetchTasks();
  }, []);

  const fetchTasks = async () => {
    try {
      setLoading(true);
      const res = await axios.get('/api/tasks/available');
      
      if (res.data.success) {
        setTasks(res.data.data || []);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error fetching tasks');
    } finally {
      setLoading(false);
    }
  };

  // Filter tasks based on search and category
  const filteredTasks = tasks.filter(task => {
    const matchesSearch = !search || 
      task.title.toLowerCase().includes(search.toLowerCase()) ||
      task.description.toLowerCase().includes(search.toLowerCase());
    
    const matchesCategory = !category || task.category === category;
    
    return matchesSearch && matchesCategory;
  });

  // Get unique categories for filter dropdown
  const categories = [...new Set(tasks.map(t => t.category))];

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">Loading tasks...</div>
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold mb-8">Task Marketplace</h1>

      {/* Filters */}
      <div className="card mb-8">
        <div className="grid md:grid-cols-3 gap-4">
          {/* Search */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Search
            </label>
            <input
              type="text"
              className="input"
              placeholder="Search tasks..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          
          {/* Category Filter */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Category
            </label>
            <select
              className="input"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            >
              <option value="">All Categories</option>
              {categories.map(cat => (
                <option key={cat} value={cat}>{cat}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-100 text-red-700 p-4 rounded-lg mb-8">
          {error}
        </div>
      )}

      {/* Tasks Grid */}
      {filteredTasks.length === 0 ? (
        <div className="text-center py-12 text-gray-500">
          No tasks found. Check back later!
        </div>
      ) : (
        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
          {filteredTasks.map(task => (
            <TaskCard key={task.id} task={task} />
          ))}
        </div>
      )}
    </div>
  );
}

// Task Card Component
function TaskCard({ task }: { task: Task }) {
  return (
    <Link href={`/tasks/${task.id}`}>
      <div className="card hover:shadow-lg transition-shadow cursor-pointer h-full">
        {/* Header */}
        <div className="flex justify-between items-start mb-3">
          <span className="px-2 py-1 bg-primary-100 text-primary-700 text-xs font-medium rounded">
            {task.category || 'General'}
          </span>
          <span className="font-bold text-primary-600">
            💰 {task.credits} credits
          </span>
        </div>

        {/* Title & Description */}
        <h3 className="text-lg font-semibold mb-2 line-clamp-2">
          {task.title}
        </h3>
        <p className="text-gray-600 text-sm mb-4 line-clamp-3">
          {task.description}
        </p>

        {/* Skills */}
        <div className="flex flex-wrap gap-1 mb-4">
          {(task.skills || []).slice(0, 3).map((skill, idx) => (
            <span 
              key={idx}
              className="px-2 py-0.5 bg-gray-100 text-gray-600 text-xs rounded"
            >
              {skill}
            </span>
          ))}
          {(task.skills || []).length > 3 && (
            <span className="text-xs text-gray-500">
              +{task.skills.length - 3} more
            </span>
          )}
        </div>

        {/* Footer */}
        <div className="flex justify-between items-center text-sm text-gray-500 border-t pt-3">
          <span>⏱️ {task.estimatedHours || 0}h</span>
          <span>📅 {task.deadline ? new Date(task.deadline).toLocaleDateString() : 'No deadline'}</span>
        </div>
      </div>
    </Link>
  );
}
