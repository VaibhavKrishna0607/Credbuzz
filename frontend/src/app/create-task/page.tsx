'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import ProtectedRoute from '@/components/ProtectedRoute';
import axios from 'axios';
import toast from 'react-hot-toast';

/**
 * ============================================
 * LEARNING NOTE: Create Task Page
 * ============================================
 * 
 * This is your CreateTask.jsx adapted for Next.js.
 * Notice how it's wrapped with ProtectedRoute.
 */

const CATEGORIES = [
  'Web Development',
  'Mobile Development',
  'Design',
  'Writing',
  'Data Entry',
  'Research',
  'Marketing',
  'Other'
];

function CreateTaskContent() {
  const router = useRouter();
  const { user, updateUser } = useAuth();
  
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    credits: 10,
    category: '',
    skills: '',
    estimatedHours: 1,
    deadline: ''
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Validation
    if (!formData.title || !formData.description || !formData.category || !formData.deadline) {
      toast.error('Please fill in all required fields');
      return;
    }

    if (formData.credits > (user?.credits || 0)) {
      toast.error('Insufficient credits');
      return;
    }

    setLoading(true);

    try {
      // Convert skills string to array
      const skillsArray = formData.skills
        .split(',')
        .map(s => s.trim())
        .filter(s => s.length > 0);

      const res = await axios.post('/api/tasks', {
        title: formData.title,
        description: formData.description,
        credits: Number(formData.credits),
        category: formData.category,
        skills: skillsArray,
        estimatedHours: Number(formData.estimatedHours),
        deadline: new Date(formData.deadline).toISOString()
      });

      if (res.data.success) {
        toast.success('Task created successfully!');
        
        // Update user credits in context
        if (user) {
          updateUser({ credits: user.credits - formData.credits });
        }
        
        router.push('/my-tasks');
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Error creating task');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="max-w-2xl mx-auto">
        <h1 className="text-3xl font-bold mb-8 text-slate-100">Create New Task</h1>

        {/* Credits Display */}
        <div className="card mb-6 bg-primary-900/30 border-primary-700">
          <div className="flex justify-between items-center">
            <span className="text-slate-300">Your Available Credits:</span>
            <span className="text-2xl font-bold text-primary-400">
              💰 {user?.credits || 0}
            </span>
          </div>
        </div>

        {/* Create Task Form */}
        <form onSubmit={handleSubmit} className="card">
          <div className="space-y-6">
            {/* Title */}
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Task Title *
              </label>
              <input
                type="text"
                name="title"
                className="input"
                placeholder="e.g., Build a responsive landing page"
                value={formData.title}
                onChange={handleChange}
                maxLength={100}
                required
              />
            </div>

            {/* Description */}
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Description *
              </label>
              <textarea
                name="description"
                className="input min-h-[150px]"
                placeholder="Describe your task in detail. What do you need done? What are the requirements?"
                value={formData.description}
                onChange={handleChange}
                maxLength={1000}
                required
              />
              <p className="text-xs text-slate-500 mt-1">
                {formData.description.length}/1000 characters
              </p>
            </div>

            {/* Category & Credits Row */}
            <div className="grid md:grid-cols-2 gap-4">
              {/* Category */}
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">
                  Category *
                </label>
                <select
                  name="category"
                  className="input"
                  value={formData.category}
                  onChange={handleChange}
                  required
                >
                  <option value="">Select a category</option>
                  {CATEGORIES.map(cat => (
                    <option key={cat} value={cat}>{cat}</option>
                  ))}
                </select>
              </div>

              {/* Credits */}
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">
                  Credits Reward *
                </label>
                <input
                  type="number"
                  name="credits"
                  className="input"
                  min={1}
                  max={user?.credits || 100}
                  value={formData.credits}
                  onChange={handleChange}
                  required
                />
                <p className="text-xs text-slate-500 mt-1">
                  Maximum: {user?.credits || 0} credits
                </p>
              </div>
            </div>

            {/* Skills */}
            <div>
              <label className="block text-sm font-medium text-slate-300 mb-1">
                Required Skills
              </label>
              <input
                type="text"
                name="skills"
                className="input"
                placeholder="e.g., React, TypeScript, CSS (comma-separated)"
                value={formData.skills}
                onChange={handleChange}
              />
            </div>

            {/* Estimated Hours & Deadline Row */}
            <div className="grid md:grid-cols-2 gap-4">
              {/* Estimated Hours */}
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">
                  Estimated Hours *
                </label>
                <input
                  type="number"
                  name="estimatedHours"
                  className="input"
                  min={1}
                  max={100}
                  value={formData.estimatedHours}
                  onChange={handleChange}
                  required
                />
              </div>

              {/* Deadline */}
              <div>
                <label className="block text-sm font-medium text-slate-300 mb-1">
                  Deadline *
                </label>
                <input
                  type="date"
                  name="deadline"
                  className="input"
                  min={new Date().toISOString().split('T')[0]}
                  value={formData.deadline}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            {/* Submit Button */}
            <div className="pt-4 border-t">
              <button
                type="submit"
                disabled={loading || formData.credits > (user?.credits || 0)}
                className="w-full btn-primary py-3 text-lg disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? 'Creating...' : `Create Task (${formData.credits} credits)`}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}

// Wrap with ProtectedRoute
export default function CreateTaskPage() {
  return (
    <ProtectedRoute>
      <CreateTaskContent />
    </ProtectedRoute>
  );
}
