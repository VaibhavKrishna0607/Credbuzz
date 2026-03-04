package com.credbuzz.entity;

/**
 * Reasons for filing a dispute
 */
public enum DisputeReason {
    /**
     * Work doesn't meet requirements
     */
    INCOMPLETE_WORK,
    
    /**
     * Work quality below expectations
     */
    POOR_QUALITY,
    
    /**
     * No communication from other party
     */
    NO_COMMUNICATION,
    
    /**
     * Missed deadline significantly
     */
    MISSED_DEADLINE,
    
    /**
     * Work differs from what was promised
     */
    NOT_AS_DESCRIBED,
    
    /**
     * Payment/escrow issues
     */
    PAYMENT_ISSUE,
    
    /**
     * Suspected fraud or scam
     */
    FRAUD,
    
    /**
     * Creator unfairly rejected work (bidder filing)
     */
    UNFAIR_REJECTION,
    
    /**
     * Other reason
     */
    OTHER
}
