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

export interface ScoredBid {
  bidId: number;
  bidderId: number;
  bidderName: string;
  proposedCredits: number;
  proposedCompletionDays: number;
  proposalMessage?: string;
  totalScore: number;
  skillMatchScore: number;
  completionRate: number;
  creditFairnessScore: number;
  rank: number;
  usedMlPrediction?: boolean;
  mlConfidence?: number;
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
  const [scoredBids, setScoredBids] = useState<ScoredBid[]>([]);
  const [loading, setLoading] = useState(true);
  const [closingAuction, setClosingAuction] = useState(false);
  const [showScores, setShowScores] = useState(false);

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

  const fetchRankedBids = async () => {
    try {
      const res = await axios.get(`/api/tasks/${taskId}/bids/ranked`);
      if (res.data.success) {
        setScoredBids(res.data.data || []);
        setShowScores(true);
      }
    } catch (err: any) {
      toast.error('Error fetching ranked bids');
    }
  };

  const handleCloseAuction = async (selectedBidId?: number) => {
    try {
      setClosingAuction(true);
      const endpoint = selectedBidId 
        ? `/api/tasks/${taskId}/close-auction?bidId=${selectedBidId}`
        : `/api/tasks/${taskId}/close-auction`;
      
      const res = await axios.put(endpoint);
      
      if (res.data.success) {
        toast.success('Auction closed! Task assigned.');
        onBidSelected?.();
      }
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Error closing auction');
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

  const canCloseAuction = isCreator && 
    (taskStatus === 'OPEN' || taskStatus === 'BIDDING' || taskStatus === 'PENDING_SELECTION') && 
    bids.length > 0;
  
  const isPendingSelection = taskStatus === 'PENDING_SELECTION';

  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center">
        <h3 className="text-lg font-semibold text-slate-100">
          Bids ({bids.length})
        </h3>
        {isCreator && bids.length > 1 && (
          <button
            onClick={fetchRankedBids}
            className="text-sm text-primary-400 hover:underline"
          >
            {showScores ? 'Hide Scores' : 'Show AI Rankings'}
          </button>
        )}
      </div>

      {/* Bid Cards */}
      <div className="space-y-3">
        {(showScores ? scoredBids : bids).map((item) => {
          const bid = showScores ? item as ScoredBid : item as Bid;
          const bidId = 'bidId' in bid ? bid.bidId : bid.id;
          const bidderName = 'bidderName' in bid ? bid.bidderName : bid.bidder.name;
          const isSelected = 'selected' in bid ? bid.selected : false;
          const score = 'totalScore' in bid ? bid.totalScore : null;
          const rank = 'rank' in bid ? bid.rank : null;

          return (
            <div 
              key={bidId}
              className={`border rounded-lg p-4 ${
                isSelected 
                  ? 'border-green-500 bg-green-900/30' 
                  : rank === 1 
                    ? 'border-primary-600 bg-primary-900/30'
                    : 'border-slate-700 bg-slate-800'
              }`}
            >
              <div className="flex justify-between items-start">
                <div className="flex items-center gap-3">
                  {/* Rank Badge */}
                  {rank && (
                    <div className={`
                      w-8 h-8 rounded-full flex items-center justify-center font-bold text-sm
                      ${rank === 1 ? 'bg-yellow-500 text-yellow-900' : 
                        rank === 2 ? 'bg-slate-400 text-slate-800' : 
                        rank === 3 ? 'bg-amber-600 text-white' : 
                        'bg-slate-700 text-slate-300'}
                    `}>
                      #{rank}
                    </div>
                  )}
                  
                  <div>
                    <p className="font-medium text-slate-200">{bidderName}</p>
                    {isSelected && (
                      <span className="text-xs text-green-400 font-medium">
                        ✓ Winner
                      </span>
                    )}
                  </div>
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

              {/* Score Breakdown (for creator) */}
              {showScores && score !== null && (
                <div className="mt-3 pt-3 border-t border-slate-700">
                  <div className="flex items-center gap-4 text-xs text-slate-400">
                    <span>AI Score: <strong className="text-slate-200">{(score * 100).toFixed(0)}%</strong></span>
                    {'skillMatchScore' in bid && (
                      <>
                        <span>Skills: {((bid as ScoredBid).skillMatchScore * 100).toFixed(0)}%</span>
                        <span>Credit: {((bid as ScoredBid).creditFairnessScore * 100).toFixed(0)}%</span>
                      </>
                    )}
                  </div>
                </div>
              )}

              {/* Select Button (for creator) */}
              {canCloseAuction && !isSelected && (
                <button
                  onClick={() => handleCloseAuction(bidId)}
                  disabled={closingAuction}
                  className="mt-3 w-full btn-secondary py-2 text-sm"
                >
                  {closingAuction ? 'Selecting...' : 'Select This Bid'}
                </button>
              )}
            </div>
          );
        })}
      </div>

      {/* Auto-Select Best Bid Button - only show if not in PENDING_SELECTION */}
      {canCloseAuction && !isPendingSelection && (
        <div className="pt-4 border-t border-slate-700">
          <button
            onClick={() => handleCloseAuction()}
            disabled={closingAuction}
            className="w-full btn-primary py-3"
          >
            {closingAuction ? 'Closing Auction...' : '🤖 Auto-Select Best Bid (AI)'}
          </button>
          <p className="text-xs text-slate-400 text-center mt-2">
            AI will select the optimal bidder based on skills, history & price
          </p>
        </div>
      )}

      {/* Manual Selection Hint for PENDING_SELECTION */}
      {isPendingSelection && canCloseAuction && (
        <div className="pt-4 border-t border-slate-700">
          <p className="text-sm text-orange-400 text-center">
            👆 Click "Select This Bid" on your preferred bidder above
          </p>
        </div>
      )}
    </div>
  );
}
