'use client';

import { useState } from 'react';
import axios from 'axios';
import toast from 'react-hot-toast';

interface BidFormProps {
  taskId: number;
  baseCredits: number;
  onBidPlaced: () => void;
}

export default function BidForm({ taskId, baseCredits, onBidPlaced }: BidFormProps) {
  const [proposedCredits, setProposedCredits] = useState(baseCredits);
  const [proposedDays, setProposedDays] = useState(7);
  const [message, setMessage] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (proposedCredits < 1) {
      toast.error('Credits must be at least 1');
      return;
    }
    if (proposedDays < 1) {
      toast.error('Completion days must be at least 1');
      return;
    }

    try {
      setSubmitting(true);
      const res = await axios.post(`/api/tasks/${taskId}/bids`, {
        proposedCredits,
        proposedCompletionDays: proposedDays,
        proposalMessage: message
      });

      if (res.data.success) {
        toast.success('Bid placed successfully!');
        onBidPlaced();
        // Reset form
        setMessage('');
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Error placing bid');
    } finally {
      setSubmitting(false);
    }
  };

  const creditDiff = proposedCredits - baseCredits;

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <h3 className="text-lg font-semibold text-slate-100">Place Your Bid</h3>

      {/* Proposed Credits */}
      <div>
        <label className="block text-sm font-medium text-slate-300 mb-1">
          Your Price (Credits)
        </label>
        <div className="relative">
          <input
            type="number"
            min={1}
            value={proposedCredits}
            onChange={(e) => setProposedCredits(Number(e.target.value))}
            className="input pr-20"
          />
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-slate-400">
            credits
          </span>
        </div>
        <p className={`text-sm mt-1 ${creditDiff < 0 ? 'text-green-400' : creditDiff > 0 ? 'text-amber-400' : 'text-slate-400'}`}>
          {creditDiff < 0 
            ? `${Math.abs(creditDiff)} below asking price` 
            : creditDiff > 0 
              ? `${creditDiff} above asking price`
              : 'Same as asking price'}
        </p>
      </div>

      {/* Proposed Completion Days */}
      <div>
        <label className="block text-sm font-medium text-slate-300 mb-1">
          Completion Time (Days)
        </label>
        <div className="relative">
          <input
            type="number"
            min={1}
            max={365}
            value={proposedDays}
            onChange={(e) => setProposedDays(Number(e.target.value))}
            className="input pr-16"
          />
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-slate-400">
            days
          </span>
        </div>
      </div>

      {/* Proposal Message */}
      <div>
        <label className="block text-sm font-medium text-slate-300 mb-1">
          Your Proposal (Optional)
        </label>
        <textarea
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder="Describe why you're the best fit for this task..."
          className="input min-h-[100px]"
          maxLength={1000}
        />
        <p className="text-sm text-slate-400 text-right">
          {message.length}/1000
        </p>
      </div>

      {/* Submit Button */}
      <button
        type="submit"
        disabled={submitting}
        className="w-full btn-primary py-3"
      >
        {submitting ? 'Placing Bid...' : '🎯 Place Bid'}
      </button>
    </form>
  );
}
