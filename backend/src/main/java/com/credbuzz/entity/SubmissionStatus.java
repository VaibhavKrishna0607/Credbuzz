package com.credbuzz.entity;

/**
 * Submission lifecycle states
 */
public enum SubmissionStatus {
    /**
     * Submission pending AI review
     */
    PENDING_AI_REVIEW,
    
    /**
     * AI review completed, awaiting creator review
     */
    PENDING_CREATOR_REVIEW,
    
    /**
     * Creator approved the submission
     */
    APPROVED,
    
    /**
     * Creator requested revisions
     */
    REVISION_REQUESTED,
    
    /**
     * Submission is under dispute
     */
    DISPUTED,
    
    /**
     * Submission rejected after dispute
     */
    REJECTED
}
