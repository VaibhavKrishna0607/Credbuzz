package com.credbuzz.entity;

/**
 * Dispute lifecycle states
 */
public enum DisputeStatus {
    /**
     * Dispute filed, awaiting response
     */
    OPEN,
    
    /**
     * Response received, under review
     */
    UNDER_REVIEW,
    
    /**
     * Additional information requested
     */
    PENDING_INFO,
    
    /**
     * Resolution reached
     */
    RESOLVED,
    
    /**
     * Dispute cancelled by filer
     */
    CANCELLED
}
