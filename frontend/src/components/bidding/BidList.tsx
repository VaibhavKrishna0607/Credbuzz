'use client';

import { useState, useEffect } from 'react';
import axios from 'axios';
import toast from 'react-hot-toast';

export interface Bid {
  id: number;
  proposedCredits: number;
  proposedCompletionDays: number;
  proposalMessage?: string;
  selected: boolean;
  createdAt: string;
  bidder: {
    id: number;
    name: string;
    avatar?: string;
  };
}

interface BidListProps {
  taskId: number;
  isCreator: boolean;
  taskStatus: string;
  onBidSelected?: () => void;
  refreshKey?: number;
}

export default function BidList({ 
  taskId, 
  isCreator, 
  taskStatus, 
  onBidSelected,
  refreshKey 
}: BidListProps) {
  const [bids, setBids] = useState<Bid[]>([]);
  const [loading, setLoading] = useState(true);
  const [closingAuction, setClosingAuction] = useState(false);

  useEffect(() => {
    fetchBids();
  }, [taskId, refreshKey]);

  const fetchBids = async () => {
    try {
      setLoading(true);
      const res = await axios.get(`/api/tasks/${taskId}/bids`);
      if (res.data.success) {
        setBids(res.data.data || []);
      }
    } catch (err: any) {
      console.error('Error fetching bids:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleSelectBid = async (selectedBidId: number) => {
    try {
      setClosingAuction(true);
      const res = await axios.put(`/api/tasks/${taskId}/close-auction?bidId=${selectedBidId}`);
      
      if (res.data.success) {
        toast.success('Bid selected! Task assigned.');
        onBidSelected?.();
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Error selecting bid');
    } finally {
      setClosingAuction(false);
    }
  };

  if (loading) {
    return <div className="text-slate-400 py-4">Loading bids...</div>;
  }

  if (bids.length === 0) {
    return (
      <div className="text-center py-8 text-slate-400">
        <p className="text-xl mb-2">🔔</p>
        <p>No bids yet. Be the first to bid!</p>
      </div>
    );
  }

  const canSelect = isCreator && 
    (taskStatus === 'BIDDING' || taskStatus === 'PENDING_SELECTION') && 
    bids.length > 0;

  return (
    <div className="space-y-4">
      <h3 className="text-lg font-semibold text-slate-100">
        Bids ({bids.length})
      </h3>

      {/* Bid Cards */}
      <div className="space-y-3">
        {bids.map((bid) => (
          <div 
            key={bid.id}
            className={`border rounded-lg p-4 ${
              bid.selected 
                ? 'border-green-500 bg-green-900/30' 
                : 'border-slate-700 bg-slate-800'
            }`}
          >
            <div className="flex justify-between items-start">
              <div>
                <p className="font-medium text-slate-200">{bid.bidder.name}</p>
                {bid.selected && (
                  <span className="text-xs text-green-400 font-medium">
                    ✓ Winner
                  </span>
                )}
              </div>

              <div className="text-right">
                <p className="font-bold text-lg text-primary-400">
                  {bid.proposedCredits} credits
                </p>
                <p className="text-sm text-slate-400">
                  {bid.proposedCompletionDays} days
                </p>
              </div>
            </div>

            {/* Proposal Message */}
            {bid.proposalMessage && (
              <p className="mt-3 text-sm text-slate-300 bg-slate-700/50 p-2 rounded">
                "{bid.proposalMessage}"
              </p>
            )}

            {/* Select Button (for creator) */}
            {canSelect && !bid.selected && (
              <button
                onClick={() => handleSelectBid(bid.id)}
                disabled={closingAuction}
                className="mt-3 w-full btn-secondary py-2 text-sm"
              >
                {closingAuction ? 'Selecting...' : 'Select This Bid'}
              </button>
            )}
          </div>
        ))}
      </div>

      {/* Selection hint */}
      {canSelect && (
        <p className="text-xs text-slate-500 text-center">
          Click "Select This Bid" to assign the task to that bidder
        </p>
      )}
    </div>
  );
}
