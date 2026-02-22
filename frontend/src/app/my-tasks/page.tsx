'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { useRouter } from 'next/navigation';
import axios from 'axios';

/**
 * My Tasks Page - Shows tasks created by or assigned to the current user
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
  assignee?: {
    id: number;
    name: string;
    avatar?: string;
    credits?: number;
  };
}

export default function MyTasksPage() {
  const { user, loading: authLoading } = useAuth();
  const router = useRouter();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState<'posted' | 'claimed'>('posted');

  useEffect(() => {
    if (!authLoading && !user) {
      router.push('/login');
    }
  }, [user, authLoading, router]);

  useEffect(() => {
    if (user) {
      fetchMyTasks();
    }
  }, [user]);

  const fetchMyTasks = async () => {
    try {
      setLoading(true);
      const res = await axios.get('/api/users/my-tasks');
      if (res.data.success) {
        setTasks(res.data.data || []);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Error fetching tasks');
    } finally {
      setLoading(false);
    }
  };

  // Filter tasks based on active tab
  const postedTasks = tasks.filter(task => task.poster?.id === user?.id);
  const claimedTasks = tasks.filter(task => task.assignee?.id === user?.id);

  const displayedTasks = activeTab === 'posted' ? postedTasks : claimedTasks;

  if (authLoading || loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">Loading...</div>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold mb-8">My Tasks</h1>

      {/* Tabs */}
      <div className="flex border-b mb-6">
        <button
          className={`px-6 py-3 font-medium ${
            activeTab === 'posted'
              ? 'border-b-2 border-primary-600 text-primary-600'
              : 'text-gray-500 hover:text-gray-700'
          }`}
          onClick={() => setActiveTab('posted')}
        >
          Posted by Me ({postedTasks.length})
        </button>
        <button
          className={`px-6 py-3 font-medium ${
            activeTab === 'claimed'
              ? 'border-b-2 border-primary-600 text-primary-600'
              : 'text-gray-500 hover:text-gray-700'
          }`}
          onClick={() => setActiveTab('claimed')}
        >
          Claimed by Me ({claimedTasks.length})
        </button>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-100 text-red-700 p-4 rounded-lg mb-8">
          {error}
        </div>
      )}

      {/* Tasks List */}
      {displayedTasks.length === 0 ? (
        <div className="text-center py-12 text-gray-500">
          {activeTab === 'posted' 
            ? "You haven't posted any tasks yet." 
            : "You haven't claimed any tasks yet."}
          <div className="mt-4">
            {activeTab === 'posted' ? (
              <Link href="/create-task" className="btn-primary">
                Create Your First Task
              </Link>
            ) : (
              <Link href="/tasks" className="btn-primary">
                Browse Available Tasks
              </Link>
            )}
          </div>
        </div>
      ) : (
        <div className="space-y-4">
          {displayedTasks.map(task => (
            <TaskRow key={task.id} task={task} isPosted={activeTab === 'posted'} />
          ))}
        </div>
      )}
    </div>
  );
}

// Task Row Component
function TaskRow({ task, isPosted }: { task: Task; isPosted: boolean }) {
  const getStatusColor = (status: string) => {
    const s = status?.toUpperCase();
    switch (s) {
      case 'OPEN': return 'bg-green-100 text-green-700';
      case 'IN_PROGRESS': return 'bg-yellow-100 text-yellow-700';
      case 'SUBMITTED': return 'bg-blue-100 text-blue-700';
      case 'COMPLETED': return 'bg-gray-100 text-gray-700';
      case 'CANCELLED': return 'bg-red-100 text-red-700';
      default: return 'bg-gray-100 text-gray-700';
    }
  };

  return (
    <Link href={`/tasks/${task.id}`}>
      <div className="card hover:shadow-lg transition-shadow cursor-pointer">
        <div className="flex justify-between items-center">
          <div className="flex-1">
            <div className="flex items-center gap-3 mb-2">
              <span className="px-2 py-1 bg-primary-100 text-primary-700 text-xs font-medium rounded">
                {task.category || 'General'}
              </span>
              <span className={`px-2 py-1 text-xs font-medium rounded ${getStatusColor(task.status)}`}>
                {task.status}
              </span>
            </div>
            <h3 className="text-lg font-semibold">{task.title}</h3>
            <p className="text-gray-600 text-sm line-clamp-1 mt-1">
              {task.description}
            </p>
          </div>
          <div className="text-right ml-4">
            <div className="font-bold text-primary-600">
              💰 {task.credits} credits
            </div>
            {task.deadline && (
              <div className="text-sm text-gray-500 mt-1">
                📅 {new Date(task.deadline).toLocaleDateString()}
              </div>
            )}
          </div>
        </div>
        {/* Show assignee for posted tasks, poster for claimed tasks */}
        <div className="mt-3 pt-3 border-t text-sm text-gray-500">
          {isPosted ? (
            task.assignee ? (
              <span>Claimed by: {task.assignee.name}</span>
            ) : (
              <span>Not claimed yet</span>
            )
          ) : (
            <span>Posted by: {task.poster?.name || 'Unknown'}</span>
          )}
        </div>
      </div>
    </Link>
  );
}
