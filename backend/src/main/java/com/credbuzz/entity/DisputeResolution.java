package com.credbuzz.entity;

/**
 * Possible dispute resolution outcomes
 */
public enum DisputeResolution {
    /**
     * Full payment to bidder (creator in wrong)
     */
    FULL_TO_BIDDER,
    
    /**
     * Full refund to creator (bidder in wrong)
     */
    FULL_TO_CREATOR,
    
    /**
     * Split payment (both parties share responsibility)
     */
    SPLIT,
    
    /**
     * Dismissed (dispute invalid or withdrawn)
     */
    DISMISSED
}
