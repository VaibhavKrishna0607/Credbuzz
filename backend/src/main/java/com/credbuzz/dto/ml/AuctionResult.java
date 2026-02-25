package com.credbuzz.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of auction closing with ML predictions
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionResult {
    
    /**
     * All bids with their computed features and scores
     */
    private List<BidFeatureSnapshot> rankedBids;

    /**
     * The winning bid
     */
    private BidFeatureSnapshot winningBid;

    /**
     * Winning bid ID (convenience field)
     */
    private Long winningBidId;

    /**
     * Winning score (heuristic or ML)
     */
    private Double winningScore;

    /**
     * Task ID
     */
    private Long taskId;

    /**
     * Whether ML predictions were used
     */
    private Boolean usedMlPrediction;

    /**
     * Reason if ML was not used
     */
    private String mlFallbackReason;

    /**
     * Total number of bids evaluated
     */
    private Integer totalBids;

    // ================================
    // CONFIDENCE-AWARE AUTOMATION (NEW)
    // ================================

    /**
     * Whether manual confirmation is required because top bids are too close.
     * If true, the system should NOT auto-assign and instead prompt the task creator.
     */
    private Boolean requiresManualConfirmation;

    /**
     * Score difference between top 2 bids (confidence margin).
     * Lower values mean less certainty in the winner selection.
     */
    private Double confidenceMargin;

    /**
     * The threshold used for requiring manual confirmation
     */
    private Double confidenceThreshold;
}
