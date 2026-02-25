'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { BidForm, BidList } from '@/components/bidding';
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
  biddingDeadline?: string;
  maxBids?: number;
  bidCount?: number;
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
  const [bidRefreshKey, setBidRefreshKey] = useState(0);
  const [activeTab, setActiveTab] = useState<'details' | 'bids'>('details');
  const [maxBidsInput, setMaxBidsInput] = useState(5);

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
        <div className="text-xl text-slate-400">Loading task...</div>
      </div>
    );
  }

  if (!task) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-slate-400">Task not found</div>
      </div>
    );
  }

const isCreator = user?.id === task.poster?.id;
const isClaimant = user?.id === task.assignee?.id;
const isBiddingOpen = task.status?.toUpperCase() === 'BIDDING';
const isAssigned = task.status?.toUpperCase() === 'ASSIGNED' || task.status?.toUpperCase() === 'IN_PROGRESS';
const isPendingSelection = task.status?.toUpperCase() === 'PENDING_SELECTION';

  const handleStartBidding = async () => {
    try {
      setSubmitting(true);
      const res = await axios.put(`/api/tasks/${taskId}/start-bidding`, {
        // Default bidding deadline: 7 days from now
        biddingDeadline: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
        maxBids: maxBidsInput
      });
      if (res.data.success) {
        toast.success(`Bidding started! ML will auto-select after ${maxBidsInput} bids.`);
        setTask(res.data.data);
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Error starting bidding');
    } finally {
      setSubmitting(false);
    }
  };

  const handleBidPlaced = () => {
    setBidRefreshKey(prev => prev + 1);
    fetchTask(); // Refresh task to update bid count
  };

  const handleAuctionClosed = () => {
    fetchTask();
  };

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="max-w-4xl mx-auto">
        {/* Task Header */}
        <div className="card mb-6">
          <div className="flex justify-between items-start mb-4">
            <div>
              <span className="px-2 py-1 bg-primary-900/50 text-primary-400 text-xs font-medium rounded border border-primary-700">
                {task.category || 'General'}
              </span>
              <h1 className="text-2xl font-bold mt-3 text-slate-100">{task.title}</h1>
            </div>
            <div className="text-right">
              <div className="text-2xl font-bold text-primary-400">
                💰 {task.credits} credits
              </div>
              <span className={`
                inline-block mt-2 px-3 py-1 rounded-full text-sm font-medium
                ${task.status?.toUpperCase() === 'OPEN' ? 'bg-green-900/50 text-green-400 border border-green-700' : ''}
                ${task.status?.toUpperCase() === 'BIDDING' ? 'bg-blue-900/50 text-blue-400 border border-blue-700' : ''}
                ${task.status?.toUpperCase() === 'AUCTION_CLOSED' ? 'bg-purple-900/50 text-purple-400 border border-purple-700' : ''}
                ${task.status?.toUpperCase() === 'PENDING_SELECTION' ? 'bg-orange-900/50 text-orange-400 border border-orange-700' : ''}
                ${task.status?.toUpperCase() === 'ASSIGNED' ? 'bg-yellow-900/50 text-yellow-400 border border-yellow-700' : ''}
                ${task.status?.toUpperCase() === 'IN_PROGRESS' ? 'bg-yellow-900/50 text-yellow-400 border border-yellow-700' : ''}
                ${task.status?.toUpperCase() === 'SUBMITTED' ? 'bg-blue-900/50 text-blue-400 border border-blue-700' : ''}
                ${task.status?.toUpperCase() === 'COMPLETED' ? 'bg-slate-700 text-slate-300 border border-slate-600' : ''}
              `}>
                {task.status === 'AUCTION_CLOSED' ? 'Auction Closed' : 
                 task.status === 'PENDING_SELECTION' ? 'Needs Your Selection' : task.status}
              </span>
            </div>
          </div>

          <p className="text-slate-300 mb-6">{task.description}</p>

          {/* Skills */}
          <div className="mb-6">
            <h3 className="text-sm font-medium text-slate-400 mb-2">Required Skills</h3>
            <div className="flex flex-wrap gap-2">
              {(task.skills || []).map((skill, idx) => (
                <span 
                  key={idx}
                  className="px-3 py-1 bg-slate-700 text-slate-300 rounded-full text-sm"
                >
                  {skill}
                </span>
              ))}
            </div>
          </div>

          {/* Task Details */}
          <div className="grid md:grid-cols-3 gap-4 py-4 border-t border-b border-slate-700">
            <div>
              <span className="text-sm text-slate-400">Estimated Time</span>
              <p className="font-medium text-slate-200">{task.estimatedHours || 'N/A'} hours</p>
            </div>
            <div>
              <span className="text-sm text-slate-400">Deadline</span>
              <p className="font-medium text-slate-200">{task.deadline ? new Date(task.deadline).toLocaleDateString() : 'No deadline'}</p>
            </div>
            <div>
              <span className="text-sm text-slate-400">Posted by</span>
              <p className="font-medium text-slate-200">{task.poster?.name || 'Unknown'}</p>
            </div>
          </div>

          {/* Assignee Info (when task is assigned) */}
          {task.assignee && (
            <div className="mt-4 p-4 bg-green-900/30 rounded-lg border border-green-700">
              <p className="text-sm text-green-400">
                <strong>Assigned to:</strong> {task.assignee.name}
              </p>
            </div>
          )}
        </div>

        {/* Tabs for Bidding */}
        {(isBiddingOpen || isPendingSelection) && user && (
          <div className="flex border-b mb-4">
            <button
              onClick={() => setActiveTab('details')}
              className={`px-4 py-2 font-medium ${
                activeTab === 'details'
                  ? 'text-primary-400 border-b-2 border-primary-400'
                  : 'text-slate-400 hover:text-slate-300'
              }`}
            >
              Details
            </button>
            <button
              onClick={() => setActiveTab('bids')}
              className={`px-4 py-2 font-medium ${
                activeTab === 'bids'
                  ? 'text-primary-400 border-b-2 border-primary-400'
                  : 'text-slate-400 hover:text-slate-300'
              }`}
            >
              🎯 Bids {isPendingSelection && '⚠️'}
            </button>
          </div>
        )}

        {/* PENDING_SELECTION - Manual Selection Required */}
        {isPendingSelection && isCreator && activeTab === 'bids' && (
          <div className="mb-6">
            {/* Warning Banner */}
            <div className="mb-4 p-4 bg-gradient-to-r from-orange-900/40 to-amber-900/40 rounded-lg border border-orange-700">
              <div className="flex items-start gap-3">
                <div className="text-2xl">⚠️</div>
                <div>
                  <h3 className="text-lg font-semibold text-orange-300 mb-1">
                    Manual Selection Required
                  </h3>
                  <p className="text-sm text-orange-400/90">
                    The ML system couldn't confidently pick a winner because the top bids were too close in score. 
                    Please review the bids below and manually select your preferred bidder.
                  </p>
                </div>
              </div>
            </div>

            {/* Bid List for Manual Selection */}
            <div className="card">
              <BidList
                taskId={task.id}
                isCreator={isCreator}
                taskStatus={task.status}
                onBidSelected={handleAuctionClosed}
                refreshKey={bidRefreshKey}
              />
            </div>
          </div>
        )}

        {/* PENDING_SELECTION - Non-creator view */}
        {isPendingSelection && !isCreator && activeTab === 'details' && (
          <div className="card mb-6">
            <div className="text-center py-4">
              <div className="text-4xl mb-2">⏳</div>
              <p className="text-lg font-medium text-orange-400 mb-2">Awaiting Manual Selection</p>
              <p className="text-slate-400">
                The task creator is reviewing bids and will select a winner soon.
              </p>
            </div>
          </div>
        )}

        {/* Bidding Section */}
        {isBiddingOpen && user && activeTab === 'bids' && (
          <div className="mb-6">
            {/* Bid Progress Indicator */}
            {task.maxBids && (
              <div className="mb-4 p-4 bg-gradient-to-r from-purple-900/40 to-blue-900/40 rounded-lg border border-purple-700">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-sm font-medium text-purple-300">
                    🤖 ML Auto-Selection Progress
                  </span>
                  <span className="text-sm text-purple-400">
                    {task.bidCount || 0} / {task.maxBids} bids
                  </span>
                </div>
                <div className="w-full bg-purple-900/50 rounded-full h-2.5">
                  <div
                    className="bg-purple-500 h-2.5 rounded-full transition-all duration-500"
                    style={{ width: `${Math.min(100, ((task.bidCount || 0) / task.maxBids) * 100)}%` }}
                  />
                </div>
                <p className="text-xs text-purple-400 mt-2">
                  {(task.bidCount || 0) >= task.maxBids
                    ? '✅ Threshold reached! ML is selecting the best bidder...'
                    : `ML will automatically select the best bidder when ${task.maxBids} bids are received`}
                </p>
              </div>
            )}
            
            <div className="grid md:grid-cols-2 gap-6">
            {/* Bid Form (for non-creators) */}
            {!isCreator && (
              <div className="card">
                <BidForm
                  taskId={task.id}
                  baseCredits={task.credits}
                  onBidPlaced={handleBidPlaced}
                />
              </div>
            )}

            {/* Bid List */}
            <div className={`card ${isCreator ? 'md:col-span-2' : ''}`}>
              <BidList
                taskId={task.id}
                isCreator={isCreator}
                taskStatus={task.status}
                onBidSelected={handleAuctionClosed}
                refreshKey={bidRefreshKey}
              />
            </div>
            </div>
          </div>
        )}

        {/* Action Buttons (non-bidding actions) */}
        {user && activeTab === 'details' && (
          <div className="card mb-6">
            {/* OPEN task - creator can start bidding */}
            {task.status?.toUpperCase() === 'OPEN' && isCreator && (
              <div className="text-center py-4">
                <div className="text-4xl mb-2">📢</div>
                <p className="text-lg font-medium text-slate-300 mb-2">Task is ready</p>
                <p className="text-slate-400 mb-4">Start accepting bids from skilled users.</p>
                
                {/* Max Bids Setting */}
                <div className="flex items-center justify-center gap-4 mb-4">
                  <label className="text-sm text-slate-400">Auto-close after:</label>
                  <select
                    value={maxBidsInput}
                    onChange={(e) => setMaxBidsInput(Number(e.target.value))}
                    className="px-3 py-2 bg-slate-700 border border-slate-600 rounded-lg text-sm text-slate-200"
                  >
                    {[3, 5, 7, 10, 15, 20].map(n => (
                      <option key={n} value={n}>{n} bids</option>
                    ))}
                  </select>
                  <span className="text-xs text-slate-500">ML will pick the best</span>
                </div>
                
                <button
                  onClick={handleStartBidding}
                  disabled={submitting}
                  className="btn-primary py-3 px-8"
                >
                  {submitting ? 'Starting...' : '🎯 Start Bidding'}
                </button>
              </div>
            )}

            {/* OPEN task - non-creator view */}
            {task.status?.toUpperCase() === 'OPEN' && !isCreator && (
              <div className="text-center py-4">
                <div className="text-4xl mb-2">⏳</div>
                <p className="text-lg font-medium text-slate-400">Bidding not yet started</p>
                <p className="text-slate-500">The task creator hasn't opened bidding yet.</p>
              </div>
            )}

            {/* Open for bidding info */}
            {isBiddingOpen && !isCreator && (
              <div className="text-center py-4">
                <div className="text-4xl mb-2">🎯</div>
                <p className="text-lg font-medium text-primary-400 mb-2">This task is open for bidding!</p>
                <p className="text-slate-400 mb-4">Click the "Bids" tab to place your bid.</p>
                <button
                  onClick={() => setActiveTab('bids')}
                  className="btn-primary py-2 px-6"
                >
                  Place a Bid
                </button>
              </div>
            )}

            {/* Creator waiting for bids */}
            {isBiddingOpen && isCreator && (
              <div className="text-center py-4">
                <div className="text-4xl mb-2">⏳</div>
                <p className="text-lg font-medium text-blue-400 mb-2">Waiting for bids</p>
                <p className="text-slate-400 mb-4">View incoming bids and select a winner when ready.</p>
                <button
                  onClick={() => setActiveTab('bids')}
                  className="btn-primary py-2 px-6"
                >
                  View Bids
                </button>
              </div>
            )}

            {/* Task assigned - assignee view */}
            {isAssigned && isClaimant && (
              <div className="text-center py-4">
                <div className="text-4xl mb-2">🎉</div>
                <p className="text-lg font-medium text-green-400 mb-2">You won this auction!</p>
                <p className="text-slate-400">Start working on the task and submit when complete.</p>
              </div>
            )}

            {/* Submit Work (for claimant) */}
            {(task.status?.toUpperCase() === 'IN_PROGRESS' || task.status?.toUpperCase() === 'ASSIGNED') && isClaimant && (
              <div>
                <h3 className="font-semibold mb-3 text-slate-200">Submit Your Work</h3>
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
                <h3 className="font-semibold mb-3 text-slate-200">Review Submission</h3>
                <div className="bg-slate-700 p-4 rounded-lg mb-4">
                  <p className="text-slate-200">{task.submission}</p>
                  <p className="text-sm text-slate-400 mt-2">
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
                <p className="text-lg font-medium text-green-400">Task Completed!</p>
              </div>
            )}
          </div>
        )}

        {/* Back Button */}
        <button
          onClick={() => router.back()}
          className="text-slate-400 hover:text-slate-200"
        >
          ← Back to Tasks
        </button>
      </div>
    </div>
  );
}
