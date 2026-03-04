package com.credbuzz.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Submission Entity - Tracks work submissions with versioning.
 * 
 * Each task can have multiple submissions (revisions).
 * Submissions are versioned and include:
 * - Deliverables (URLs/files)
 * - Completion notes
 * - Evidence of completion
 * - AI review results
 */
@Entity
@Table(name = "submissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitter_id", nullable = false)
    private User submitter;

    /**
     * Version number (1 = initial, 2+ = revisions)
     */
    @Column(nullable = false)
    private Integer versionNumber;

    /**
     * Main deliverable content/description
     */
    @Column(length = 5000)
    private String content;

    /**
     * Completion notes explaining what was done
     */
    @Column(length = 2000)
    private String completionNotes;

    /**
     * List of deliverable URLs (files, repos, demos, etc.)
     */
    @ElementCollection
    @CollectionTable(name = "submission_deliverables", joinColumns = @JoinColumn(name = "submission_id"))
    @Column(name = "deliverable_url")
    @Builder.Default
    private List<String> deliverables = new ArrayList<>();

    /**
     * Evidence of completion (screenshots, logs, etc.)
     */
    @ElementCollection
    @CollectionTable(name = "submission_evidence", joinColumns = @JoinColumn(name = "submission_id"))
    @Column(name = "evidence_url")
    @Builder.Default
    private List<String> evidence = new ArrayList<>();

    /**
     * Current status of this submission
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status;

    // ============================================
    // AI Review Fields
    // ============================================

    /**
     * AI-assessed requirement coverage (0-100)
     */
    @Column
    private Double aiRequirementCoverage;

    /**
     * AI-assessed alignment with original proposal (0-100)
     */
    @Column
    private Double aiProposalAlignment;

    /**
     * AI-assessed technical quality score (0-100)
     */
    @Column
    private Double aiTechnicalScore;

    /**
     * AI-assessed deadline compliance (0 = late, 100 = on-time/early)
     */
    @Column
    private Double aiDeadlineCompliance;

    /**
     * Combined AI compliance score (weighted average)
     */
    @Column
    private Double aiFinalScore;

    /**
     * When AI review was completed
     */
    @Column
    private LocalDateTime aiReviewedAt;

    // ============================================
    // Timestamps
    // ============================================

    @Column
    private LocalDateTime submittedAt;

    @Column
    private LocalDateTime reviewedAt;

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        submittedAt = LocalDateTime.now();
        if (status == null) {
            status = SubmissionStatus.PENDING_AI_REVIEW;
        }
        if (versionNumber == null) {
            versionNumber = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
