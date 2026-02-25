package com.credbuzz.entity;

import java.util.Set;

/**
 * ============================================
 * LEARNING NOTE: Enum for Task Status
 * ============================================
 * 
 * Task lifecycle with auction support:
 * OPEN -> BIDDING -> AUCTION_CLOSED -> (ASSIGNED | PENDING_SELECTION) -> IN_PROGRESS -> SUBMITTED -> COMPLETED
 *                                                                                                  -> CANCELLED (at any point by poster)
 * 
 * PENDING_SELECTION: New status for confidence-aware automation.
 * When ML cannot confidently pick a winner (top bids too close), 
 * task enters this status requiring manual selection by creator.
 */
public enum TaskStatus {
    OPEN,
    BIDDING,
    AUCTION_CLOSED,
    PENDING_SELECTION,  // NEW: Requires manual bid selection
    ASSIGNED,
    IN_PROGRESS,
    SUBMITTED,
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
            case PENDING_SELECTION -> Set.of(ASSIGNED, CANCELLED);  // Manual selection leads to ASSIGNED
            case ASSIGNED -> Set.of(IN_PROGRESS, CANCELLED);
            case IN_PROGRESS -> Set.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> Set.of(COMPLETED, IN_PROGRESS, CANCELLED);
            case COMPLETED, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(TaskStatus newStatus) {
        return getAllowedTransitions().contains(newStatus);
    }
}
