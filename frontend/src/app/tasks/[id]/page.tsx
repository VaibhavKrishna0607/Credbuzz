'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import axios from 'axios';
import toast from 'react-hot-toast';

/**
 * ============================================
 * LEARNING NOTE: Dynamic Route in Next.js
 * ============================================
 * 
 * File: /app/tasks/[id]/page.tsx
 * Route: /tasks/123 (where 123 is the id)
 * 
 * This is like your React Router: /tasks/:id
 * 
 * To get the id parameter:
 * - React Router: const { id } = useParams();
 * - Next.js: const params = useParams(); params.id
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
  submission?: string;
  submittedAt?: string;
  rejectionReason?: string;
  aiReview?: string;
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

export default function SingleTaskPage() {
  const params = useParams();
  const router = useRouter();
  const { user } = useAuth();
  
  const [task, setTask] = useState<Task | null>(null);
  const [loading, setLoading] = useState(true);
  const [submissionContent, setSubmissionContent] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const taskId = params.id;

  useEffect(() => {
    if (taskId) {
      fetchTask();
    }
  }, [taskId]);

  const fetchTask = async () => {
    try {
      const res = await axios.get(`/api/tasks/${taskId}`);
      if (res.data.success) {
        setTask(res.data.data);
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Error fetching task');
      router.push('/tasks');
    } finally {
      setLoading(false);
    }
  };

  const handleClaim = async () => {
    try {
      setSubmitting(true);
      const res = await axios.put(`/api/tasks/${taskId}/claim`);
      if (res.data.success) {
        toast.success('Task claimed successfully!');
        setTask(res.data.data);
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Error claiming task');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = async () => {
    if (!submissionContent.trim()) {
      toast.error('Please add submission content');
      return;
    }

    try {
      setSubmitting(true);
      const res = await axios.put(`/api/tasks/${taskId}/submit`, {
        content: submissionContent
      });
      if (res.data.success) {
        toast.success('Task submitted for review!');
        setTask(res.data.data);
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Error submitting task');
    } finally {
      setSubmitting(false);
    }
  };

  const handleApprove = async () => {
    try {
      setSubmitting(true);
      const res = await axios.put(`/api/tasks/${taskId}/approve`);
      if (res.data.success) {
        toast.success('Task approved! Credits transferred.');
        setTask(res.data.data);
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Error approving task');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">Loading task...</div>
      </div>
    );
  }

  if (!task) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">Task not found</div>
      </div>
    );
  }

const isCreator = user?.id === task.poster?.id;
const isClaimant = user?.id === task.assignee?.id;

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="max-w-4xl mx-auto">
        {/* Task Header */}
        <div className="card mb-6">
          <div className="flex justify-between items-start mb-4">
            <div>
              <span className="px-2 py-1 bg-primary-100 text-primary-700 text-xs font-medium rounded">
                {task.category || 'General'}
              </span>
              <h1 className="text-2xl font-bold mt-3">{task.title}</h1>
            </div>
            <div className="text-right">
              <div className="text-2xl font-bold text-primary-600">
                💰 {task.credits} credits
              </div>
              <span className={`
                inline-block mt-2 px-3 py-1 rounded-full text-sm font-medium
                ${task.status?.toUpperCase() === 'OPEN' ? 'bg-green-100 text-green-700' : ''}
                ${task.status?.toUpperCase() === 'IN_PROGRESS' ? 'bg-yellow-100 text-yellow-700' : ''}
                ${task.status?.toUpperCase() === 'SUBMITTED' ? 'bg-blue-100 text-blue-700' : ''}
                ${task.status?.toUpperCase() === 'COMPLETED' ? 'bg-gray-100 text-gray-700' : ''}
              `}>
                {task.status}
              </span>
            </div>
          </div>

          <p className="text-gray-700 mb-6">{task.description}</p>

          {/* Skills */}
          <div className="mb-6">
            <h3 className="text-sm font-medium text-gray-500 mb-2">Required Skills</h3>
            <div className="flex flex-wrap gap-2">
              {(task.skills || []).map((skill, idx) => (
                <span 
                  key={idx}
                  className="px-3 py-1 bg-gray-100 text-gray-700 rounded-full text-sm"
                >
                  {skill}
                </span>
              ))}
            </div>
          </div>

          {/* Task Details */}
          <div className="grid md:grid-cols-3 gap-4 py-4 border-t border-b">
            <div>
              <span className="text-sm text-gray-500">Estimated Time</span>
              <p className="font-medium">{task.estimatedHours || 'N/A'} hours</p>
            </div>
            <div>
              <span className="text-sm text-gray-500">Deadline</span>
              <p className="font-medium">{task.deadline ? new Date(task.deadline).toLocaleDateString() : 'No deadline'}</p>
            </div>
            <div>
              <span className="text-sm text-gray-500">Posted by</span>
              <p className="font-medium">{task.poster?.name || 'Unknown'}</p>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        {user && (
          <div className="card mb-6">
            {/* Claim Button (for open tasks) */}
            {task.status?.toUpperCase() === 'OPEN' && !isCreator && (
              <button
                onClick={handleClaim}
                disabled={submitting}
                className="w-full btn-primary py-3 text-lg"
              >
                {submitting ? 'Claiming...' : 'Claim This Task'}
              </button>
            )}

            {/* Submit Work (for claimant) */}
            {task.status?.toUpperCase() === 'IN_PROGRESS' && isClaimant && (
              <div>
                <h3 className="font-semibold mb-3">Submit Your Work</h3>
                <textarea
                  className="input min-h-[150px] mb-3"
                  placeholder="Describe your completed work..."
                  value={submissionContent}
                  onChange={(e) => setSubmissionContent(e.target.value)}
                />
                <button
                  onClick={handleSubmit}
                  disabled={submitting}
                  className="w-full btn-primary py-3"
                >
                  {submitting ? 'Submitting...' : 'Submit Work'}
                </button>
              </div>
            )}

            {/* Approve/Reject (for creator) */}
            {task.status?.toUpperCase() === 'SUBMITTED' && isCreator && (
              <div>
                <h3 className="font-semibold mb-3">Review Submission</h3>
                <div className="bg-gray-50 p-4 rounded-lg mb-4">
                  <p className="text-gray-700">{task.submission}</p>
                  <p className="text-sm text-gray-500 mt-2">
                    Submitted: {task.submittedAt && new Date(task.submittedAt).toLocaleString()}
                  </p>
                </div>
                <div className="flex gap-4">
                  <button
                    onClick={handleApprove}
                    disabled={submitting}
                    className="flex-1 btn-primary py-3"
                  >
                    ✓ Approve & Pay
                  </button>
                  {/* TODO: Add reject functionality */}
                </div>
              </div>
            )}

            {/* Completed status */}
            {task.status?.toUpperCase() === 'COMPLETED' && (
              <div className="text-center py-4">
                <div className="text-4xl mb-2">✅</div>
                <p className="text-lg font-medium text-green-600">Task Completed!</p>
              </div>
            )}
          </div>
        )}

        {/* Back Button */}
        <button
          onClick={() => router.back()}
          className="text-gray-600 hover:text-gray-800"
        >
          ← Back to Tasks
        </button>
      </div>
    </div>
  );
}
