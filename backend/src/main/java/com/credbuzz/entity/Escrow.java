package com.credbuzz.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ============================================
 * Escrow Entity - Phase 2
 * ============================================
 * 
 * Holds credits in escrow during task execution.
 * 
 * Lifecycle:
 * 1. LOCKED - Credits deducted from creator, held in escrow
 * 2. RELEASED - Credits transferred to bidder (on approval)
 * 3. REFUNDED - Credits returned to creator (on cancellation/dispute)
 * 4. PARTIAL_RELEASE - Split between parties (dispute resolution)
 */
@Entity
@Table(name = "escrows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Escrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    /**
     * Total credits locked in escrow
     */
    @Column(nullable = false)
    private Integer lockedCredits;

    /**
     * Credits released to bidder (may be partial in disputes)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer releasedCredits = 0;

    /**
     * Credits refunded to creator (may be partial in disputes)
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer refundedCredits = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EscrowStatus status = EscrowStatus.LOCKED;

    /**
     * When escrow was created/locked
     */
    @Column(nullable = false)
    private LocalDateTime lockedAt;

    /**
     * When escrow was released/resolved
     */
    private LocalDateTime resolvedAt;

    /**
     * Resolution notes (for auditing)
     */
    @Column(length = 1000)
    private String resolutionNotes;

    /**
     * Reference to dispute if applicable
     */
    private Long disputeId;

    @PrePersist
    protected void onCreate() {
        if (lockedAt == null) {
            lockedAt = LocalDateTime.now();
        }
    }
}
