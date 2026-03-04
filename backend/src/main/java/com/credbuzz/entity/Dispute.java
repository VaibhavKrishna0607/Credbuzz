package com.credbuzz.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Dispute Entity - Handles conflicts between creators and bidders.
 * 
 * Disputes can be raised by either party when there's disagreement
 * about work quality, completion, or other issues.
 * 
 * Resolution process:
 * 1. Filing party submits complaint
 * 2. Other party can respond
 * 3. System reviews AI score + evidence
 * 4. Resolution: Full to bidder, Full to creator, or Split
 */
@Entity
@Table(name = "disputes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filed_by_id", nullable = false)
    private User filedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filed_against_id", nullable = false)
    private User filedAgainst;

    /**
     * Dispute status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeStatus status;

    /**
     * Reason category for the dispute
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DisputeReason reason;

    /**
     * Detailed complaint from filing party
     */
    @Column(length = 3000, nullable = false)
    private String complaint;

    /**
     * Response from the other party
     */
    @Column(length = 3000)
    private String response;

    /**
     * AI score at time of dispute (snapshot)
     */
    @Column
    private Double aiScoreSnapshot;

    /**
     * Final resolution decision
     */
    @Enumerated(EnumType.STRING)
    @Column
    private DisputeResolution resolution;

    /**
     * If partial resolution, percentage to bidder (0-100)
     */
    @Column
    private Integer bidderPercentage;

    /**
     * Resolution reasoning
     */
    @Column(length = 2000)
    private String resolutionNotes;

    /**
     * User who made the resolution decision (admin or system)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    // ============================================
    // Timestamps
    // ============================================

    @Column
    private LocalDateTime filedAt;

    @Column
    private LocalDateTime respondedAt;

    @Column
    private LocalDateTime resolvedAt;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        filedAt = LocalDateTime.now();
        if (status == null) {
            status = DisputeStatus.OPEN;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
