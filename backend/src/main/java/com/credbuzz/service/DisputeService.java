package com.credbuzz.service;

import com.credbuzz.entity.*;
import com.credbuzz.repository.DisputeRepository;
import com.credbuzz.repository.SubmissionRepository;
import com.credbuzz.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Dispute Service - Handles conflict resolution between creators and bidders.
 * 
 * Resolution logic:
 * - If AI score >= 70 and bidder is defendant: Likely favor bidder (work was adequate)
 * - If AI score < 50: Likely favor creator (work was poor)
 * - Middle ground (50-70): Evaluate details, may result in split
 * - Multiple disputes against same user: Increases scrutiny
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final TaskRepository taskRepository;
    private final SubmissionRepository submissionRepository;
    private final EscrowService escrowService;

    private static final double HIGH_QUALITY_THRESHOLD = 70.0;
    private static final double LOW_QUALITY_THRESHOLD = 50.0;

    /**
     * File a new dispute
     */
    @Transactional
    public Dispute fileDispute(
            Long taskId,
            User filer,
            DisputeReason reason,
            String complaint) {
        
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        // Validate task can be disputed
        if (task.getStatus() != TaskStatus.IN_REVIEW && 
            task.getStatus() != TaskStatus.SUBMITTED &&
            task.getStatus() != TaskStatus.REVISION_REQUESTED) {
            throw new RuntimeException("Cannot dispute task in status: " + task.getStatus());
        }

        // Check if dispute already exists
        if (disputeRepository.hasActiveDispute(taskId)) {
            throw new RuntimeException("Task already has an active dispute");
        }

        // Determine parties
        User defendant;
        if (filer.getId().equals(task.getPoster().getId())) {
            defendant = task.getAssignee();
        } else if (filer.getId().equals(task.getAssignee().getId())) {
            defendant = task.getPoster();
        } else {
            throw new RuntimeException("Only task creator or assignee can file a dispute");
        }

        // Get AI score
        Double aiScore = getLatestAIScore(taskId);

        // Create dispute
        Dispute dispute = Dispute.builder()
                .task(task)
                .filedBy(filer)
                .filedAgainst(defendant)
                .status(DisputeStatus.OPEN)
                .reason(reason)
                .complaint(complaint)
                .aiScoreSnapshot(aiScore)
                .build();

        Dispute savedDispute = disputeRepository.save(dispute);

        // Update task status
        task.setStatus(TaskStatus.DISPUTED);
        taskRepository.save(task);

        // Update submission status
        submissionRepository.findFirstByTaskIdOrderByVersionNumberDesc(taskId)
                .ifPresent(submission -> {
                    submission.setStatus(SubmissionStatus.DISPUTED);
                    submissionRepository.save(submission);
                });

        log.info("Dispute filed for task {} by {} against {} - Reason: {}", 
                taskId, filer.getName(), defendant.getName(), reason);

        return savedDispute;
    }

    /**
     * Submit response to a dispute
     */
    @Transactional
    public Dispute respondToDispute(Long disputeId, User respondent, String response) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        // Validate respondent
        if (!dispute.getFiledAgainst().getId().equals(respondent.getId())) {
            throw new RuntimeException("Only the defendant can respond");
        }

        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new RuntimeException("Cannot respond to dispute in status: " + dispute.getStatus());
        }

        dispute.setResponse(response);
        dispute.setRespondedAt(LocalDateTime.now());
        dispute.setStatus(DisputeStatus.UNDER_REVIEW);

        Dispute savedDispute = disputeRepository.save(dispute);
        log.info("Response submitted for dispute {} by {}", disputeId, respondent.getName());

        return savedDispute;
    }

    /**
     * Resolve a dispute with a decision.
     * Called by admin or system after review.
     */
    @Transactional
    public Dispute resolveDispute(
            Long disputeId,
            User resolver,
            DisputeResolution resolution,
            Integer bidderPercentage,
            String resolutionNotes) {
        
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        if (dispute.getStatus() == DisputeStatus.RESOLVED || 
            dispute.getStatus() == DisputeStatus.CANCELLED) {
            throw new RuntimeException("Dispute already resolved or cancelled");
        }

        Task task = dispute.getTask();

        // Update dispute
        dispute.setResolution(resolution);
        dispute.setBidderPercentage(bidderPercentage);
        dispute.setResolutionNotes(resolutionNotes);
        dispute.setResolvedBy(resolver);
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setStatus(DisputeStatus.RESOLVED);

        // Handle escrow based on resolution
        switch (resolution) {
            case FULL_TO_BIDDER -> {
                escrowService.releaseCredits(task.getId());
                task.setStatus(TaskStatus.COMPLETED);
            }
            case FULL_TO_CREATOR -> {
                escrowService.refundCredits(task.getId(), "Dispute resolved in creator's favor: " + resolutionNotes);
                task.setStatus(TaskStatus.CANCELLED);
            }
            case SPLIT -> {
                if (bidderPercentage == null || bidderPercentage < 0 || bidderPercentage > 100) {
                    throw new RuntimeException("Bidder percentage required for split resolution");
                }
                escrowService.partialRelease(task.getId(), bidderPercentage, disputeId, resolutionNotes);
                task.setStatus(TaskStatus.COMPLETED);
            }
            case DISMISSED -> {
                // Return to previous state (IN_REVIEW)
                task.setStatus(TaskStatus.IN_REVIEW);
            }
        }

        task.setCompletedAt(LocalDateTime.now());
        taskRepository.save(task);

        Dispute savedDispute = disputeRepository.save(dispute);
        log.info("Dispute {} resolved: {} (bidder%: {})", disputeId, resolution, bidderPercentage);

        return savedDispute;
    }

    /**
     * Auto-resolve dispute based on AI score (for simple cases).
     * Used when there's clear evidence from AI review.
     */
    @Transactional
    public Dispute autoResolve(Long disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        Double aiScore = dispute.getAiScoreSnapshot();
        
        if (aiScore == null) {
            log.warn("Cannot auto-resolve dispute {} - no AI score available", disputeId);
            return dispute;
        }

        boolean isBidderDefendant = dispute.getFiledAgainst().getId()
                .equals(dispute.getTask().getAssignee().getId());

        DisputeResolution suggestedResolution;
        int bidderPercentage = 50;
        String notes;

        if (aiScore >= HIGH_QUALITY_THRESHOLD) {
            if (isBidderDefendant) {
                // High quality work, bidder is defendant - favor bidder
                suggestedResolution = DisputeResolution.FULL_TO_BIDDER;
                bidderPercentage = 100;
                notes = "AI Review indicates high quality work (score: " + aiScore + 
                        "). Dispute resolved in bidder's favor.";
            } else {
                // High quality work, creator is defendant - needs manual review
                suggestedResolution = DisputeResolution.SPLIT;
                bidderPercentage = 70;
                notes = "AI Review indicates high quality work. Partial resolution.";
            }
        } else if (aiScore < LOW_QUALITY_THRESHOLD) {
            if (isBidderDefendant) {
                // Low quality work, bidder is defendant - favor creator
                suggestedResolution = DisputeResolution.FULL_TO_CREATOR;
                bidderPercentage = 0;
                notes = "AI Review indicates poor quality work (score: " + aiScore + 
                        "). Dispute resolved in creator's favor.";
            } else {
                // Low quality work, creator is defendant - unusual, needs manual review
                log.warn("Unusual dispute case: Low AI score but creator is defendant");
                return dispute;
            }
        } else {
            // Middle ground - split by default
            suggestedResolution = DisputeResolution.SPLIT;
            bidderPercentage = (int) Math.round(aiScore);
            notes = "AI Review score (" + aiScore + ") in middle range. Fair split recommended.";
        }

        return resolveDispute(disputeId, null, suggestedResolution, bidderPercentage, notes);
    }

    /**
     * Cancel a dispute (by filer only)
     */
    @Transactional
    public Dispute cancelDispute(Long disputeId, User canceller) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        if (!dispute.getFiledBy().getId().equals(canceller.getId())) {
            throw new RuntimeException("Only the filer can cancel the dispute");
        }

        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new RuntimeException("Can only cancel open disputes");
        }

        dispute.setStatus(DisputeStatus.CANCELLED);
        disputeRepository.save(dispute);

        // Return task to IN_REVIEW
        Task task = dispute.getTask();
        task.setStatus(TaskStatus.IN_REVIEW);
        taskRepository.save(task);

        log.info("Dispute {} cancelled by {}", disputeId, canceller.getName());

        return dispute;
    }

    /**
     * Get the latest AI score for a task
     */
    private Double getLatestAIScore(Long taskId) {
        return submissionRepository.findFirstByTaskIdOrderByVersionNumberDesc(taskId)
                .map(Submission::getAiFinalScore)
                .orElse(null);
    }

    // ============================================
    // QUERY METHODS
    // ============================================

    public Optional<Dispute> getDisputeByTaskId(Long taskId) {
        return disputeRepository.findByTaskId(taskId);
    }

    public Optional<Dispute> getDispute(Long disputeId) {
        return disputeRepository.findById(disputeId);
    }

    @Transactional(readOnly = true)
    public List<Dispute> getUserDisputes(User user) {
        return disputeRepository.findAllInvolvingUser(user);
    }

    @Transactional(readOnly = true)
    public List<Dispute> getOpenDisputes() {
        return disputeRepository.findByStatusOrderByFiledAtAsc(DisputeStatus.OPEN);
    }

    @Transactional(readOnly = true)
    public List<Dispute> getDisputesUnderReview() {
        return disputeRepository.findByStatus(DisputeStatus.UNDER_REVIEW);
    }

    public long getDisputeCountAgainstUser(User user) {
        return disputeRepository.countByFiledAgainst(user);
    }

    /**
     * Get dispute loss rate for a user (indicator of reliability)
     */
    public double getDisputeLossRate(User user) {
        long totalDisputes = disputeRepository.countByFiledAgainst(user);
        if (totalDisputes == 0) return 0.0;
        
        long lostDisputes = disputeRepository.countResolvedAgainstUser(user);
        return (double) lostDisputes / totalDisputes;
    }
}
