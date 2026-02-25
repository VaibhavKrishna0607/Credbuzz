package com.credbuzz.dto.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ============================================
 * Bid Feature Snapshot DTO
 * ============================================
 * 
 * Immutable snapshot of all features computed for a bid at auction close.
 * This is stored as JSON in AuctionHistory and never recomputed.
 * 
 * Features are grouped into:
 * - Proposal Features: About the bid itself
 * - Bidder Features: About the bidder's history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidFeatureSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    // ================================
    // IDENTIFIERS
    // ================================
    private Long bidId;
    private Long bidderId;
    private Long taskId;
    private String bidderName;

    // ================================
    // PROPOSAL FEATURES
    // ================================
    
    /**
     * Cosine similarity between task skills and bidder skills (0.0 - 1.0)
     * Higher = better skill match
     */
    private Double skillMatchScore;

    /**
     * Credit delta: proposedCredits - task.baseCredits
     * Negative = bidder asking for less (good for poster)
     */
    private Double creditDelta;

    /**
     * Deadline delta: proposedCompletionDays - user.avgCompletionDays
     * Negative = bidder proposing faster than their average
     */
    private Double deadlineDelta;

    /**
     * Proposed credits by bidder
     */
    private Integer proposedCredits;

    /**
     * Proposed completion days by bidder
     */
    private Integer proposedCompletionDays;

    // ================================
    // BIDDER FEATURES (HISTORICAL)
    // ================================
    
    /**
     * Bayesian-adjusted completion rate: (completed + 3) / (total + 5)
     * This prevents new users from having extreme rates
     */
    private Double completionRate;

    /**
     * Average rating received (0.0 - 5.0)
     */
    private Double avgRating;

    /**
     * Late task ratio: lateTasks / totalTasks (0.0 - 1.0)
     * Lower is better
     */
    private Double lateRatio;

    /**
     * Current workload: activeTasks / threshold (0.0 - 1.0+)
     * Lower is better (more available)
     */
    private Double workloadScore;

    /**
     * Experience level: total completed tasks
     */
    private Integer experienceLevel;

    /**
     * On-time completion rate (0.0 - 1.0)
     */
    private Double onTimeRate;

    /**
     * Bid win rate: bidsWon / totalBids (0.0 - 1.0)
     */
    private Double bidWinRate;

    /**
     * Total tasks assigned to this user
     */
    private Integer totalTasksAssigned;

    /**
     * Total tasks completed by this user
     */
    private Integer totalTasksCompleted;

    // ================================
    // COMPUTED SCORES
    // ================================

    /**
     * Heuristic score computed by BidEvaluationService
     */
    private Double heuristicScore;

    /**
     * ML-predicted success probability (0.0 - 1.0)
     * Null if ML service unavailable
     */
    private Double predictedSuccessProbability;

    /**
     * ML predicted score (alias for predictedSuccessProbability for convenience)
     */
    private Double mlPredictedScore;

    /**
     * ML prediction confidence level (0.0 - 1.0)
     */
    private Double mlConfidence;

    /**
     * Final score used for ranking (may be ML or heuristic)
     */
    private Double finalScore;

    /**
     * Whether ML prediction was used (true) or fallback to heuristic (false)
     */
    private Boolean usedMlPrediction;

    // ================================
    // TEXT ANALYSIS FEATURES (NEW)
    // ================================

    /**
     * Semantic similarity between task description and proposal text (0.0 - 1.0)
     * Higher = proposal better addresses what task description asks for
     */
    private Double proposalRelevanceScore;

    /**
     * Keyword coverage score (0.0 - 1.0)
     * How many important task keywords appear in the proposal
     */
    private Double keywordCoverageScore;

    /**
     * Combined text score from ML text analyzer (0.0 - 1.0)
     * Weighted combination of relevance and coverage
     */
    private Double combinedTextScore;

    // ================================
    // TASK CONTEXT
    // ================================
    
    /**
     * Original task credits (base credits)
     */
    private Integer taskBaseCredits;

    /**
     * Number of skills required by task
     */
    private Integer taskSkillCount;

    /**
     * Task category
     */
    private String taskCategory;
}
