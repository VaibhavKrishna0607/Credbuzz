package com.credbuzz.entity;

import java.util.Set;

/**
 * ============================================
 * LEARNING NOTE: Enum for Task Status
 * ============================================
 * 
 * Task lifecycle with auction + post-completion workflow:
 * 
 * Auction Phase:
 * OPEN -> BIDDING -> AUCTION_CLOSED -> (ASSIGNED | PENDING_SELECTION)
 * 
 * Work Phase:
 * ASSIGNED -> IN_PROGRESS -> SUBMITTED
 * 
 * Review Phase (Post-Completion Lifecycle):
 * SUBMITTED -> IN_REVIEW -> APPROVED | REVISION_REQUESTED | DISPUTED
 * 
 * Terminal States:
 * APPROVED -> COMPLETED
 * DISPUTED -> (Resolution) -> COMPLETED | CANCELLED
 * REVISION_REQUESTED -> IN_PROGRESS (max 2 revisions)
 * CANCELLED (at any point by poster before completion)
 * 
 * Status Descriptions:
 * - PENDING_SELECTION: ML confidence too low, requires manual bid selection
 * - IN_REVIEW: Submitted work under AI + creator review (48hr window)
 * - APPROVED: Creator approved, credits released
 * - REVISION_REQUESTED: Creator requested changes (max 2 times)
 * - DISPUTED: Creator/bidder dispute, pending resolution
 */
public enum TaskStatus {
    // Auction phase
    OPEN,
    BIDDING,
    AUCTION_CLOSED,
    PENDING_SELECTION,
    ASSIGNED,
    
    // Work phase
    IN_PROGRESS,
    SUBMITTED,
    
    // Review phase (Post-Completion Lifecycle)
    IN_REVIEW,
    APPROVED,
    REVISION_REQUESTED,
    DISPUTED,
    
    // Terminal states
    COMPLETED,
    CANCELLED;

    /**
     * Defines valid transitions from each status
     */
    public Set<TaskStatus> getAllowedTransitions() {
        return switch (this) {
            case OPEN -> Set.of(BIDDING, CANCELLED);
            case BIDDING -> Set.of(AUCTION_CLOSED, PENDING_SELECTION, CANCELLED);
            case AUCTION_CLOSED -> Set.of(ASSIGNED, PENDING_SELECTION, CANCELLED);
            case PENDING_SELECTION -> Set.of(ASSIGNED, CANCELLED);
            case ASSIGNED -> Set.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> Set.of(IN_REVIEW, COMPLETED, IN_PROGRESS, CANCELLED); // IN_REVIEW is new primary path
            case IN_REVIEW -> Set.of(APPROVED, REVISION_REQUESTED, DISPUTED);
            case APPROVED -> Set.of(COMPLETED);
            case REVISION_REQUESTED -> Set.of(IN_PROGRESS); // Back to work
            case DISPUTED -> Set.of(COMPLETED, CANCELLED); // Resolution determines outcome
            case COMPLETED, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(TaskStatus newStatus) {
        return getAllowedTransitions().contains(newStatus);
    }
    
    /**
     * Check if this status is in the review phase
     */
    public boolean isInReviewPhase() {
        return this == IN_REVIEW || this == APPROVED || this == REVISION_REQUESTED || this == DISPUTED;
    }
    
    /**
     * Check if this status allows escrow release
     */
    public boolean allowsEscrowRelease() {
        return this == APPROVED || this == COMPLETED;
    }
    
    /**
     * Check if this status requires escrow to be locked
     */
    public boolean requiresEscrowLocked() {
        return this == ASSIGNED || this == IN_PROGRESS || this == SUBMITTED || 
               this == IN_REVIEW || this == REVISION_REQUESTED || this == DISPUTED;
    }
}
