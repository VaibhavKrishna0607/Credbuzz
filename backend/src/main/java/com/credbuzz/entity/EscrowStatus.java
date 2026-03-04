package com.credbuzz.entity;

/**
 * Escrow lifecycle states
 */
public enum EscrowStatus {
    /**
     * Credits locked, awaiting task completion
     */
    LOCKED,
    
    /**
     * Credits fully released to bidder
     */
    RELEASED,
    
    /**
     * Credits fully refunded to creator
     */
    REFUNDED,
    
    /**
     * Credits split between parties (dispute resolution)
     */
    PARTIAL_RELEASE,
    
    /**
     * Escrow cancelled (task cancelled before work started)
     */
    CANCELLED
}
