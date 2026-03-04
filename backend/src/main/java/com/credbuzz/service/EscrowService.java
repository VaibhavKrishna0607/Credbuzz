package com.credbuzz.service;

import com.credbuzz.entity.*;
import com.credbuzz.repository.EscrowRepository;
import com.credbuzz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Escrow Service - Handles credit locking and release for task lifecycle.
 * 
 * Escrow Flow:
 * 1. LOCK: When bid is selected and task becomes ASSIGNED
 * 2. RELEASE: When creator approves completed work
 * 3. REFUND: When dispute resolves in creator's favor
 * 4. PARTIAL_RELEASE: When dispute results in split decision
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EscrowService {

    private final EscrowRepository escrowRepository;
    private final UserRepository userRepository;

    /**
     * Lock credits when a task is assigned to a bidder.
     * Credits have already been deducted from creator when task was created.
     * This creates an escrow record to track the locked funds.
     * 
     * @param task The task being assigned
     * @param bidder The winning bidder
     * @return Created escrow record
     */
    @Transactional
    public Escrow lockCredits(Task task, User bidder) {
        // Check if escrow already exists for this task
        Optional<Escrow> existingEscrow = escrowRepository.findByTask(task);
        if (existingEscrow.isPresent()) {
            log.warn("Escrow already exists for task {}. Returning existing escrow.", task.getId());
            return existingEscrow.get();
        }

        User creator = task.getPoster();
        int creditsToLock = task.getCredits();

        // Create escrow record
        Escrow escrow = Escrow.builder()
                .task(task)
                .creator(creator)
                .bidder(bidder)
                .lockedCredits(creditsToLock)
                .releasedCredits(0)
                .refundedCredits(0)
                .status(EscrowStatus.LOCKED)
                .lockedAt(LocalDateTime.now())
                .build();

        Escrow savedEscrow = escrowRepository.save(escrow);
        log.info("ESCROW LOCKED: {} credits for task {} (creator: {}, bidder: {})",
                creditsToLock, task.getId(), creator.getName(), bidder.getName());

        return savedEscrow;
    }

    /**
     * Release all escrowed credits to the bidder.
     * Called when creator approves the completed work.
     * 
     * @param taskId The task ID
     * @return Updated escrow record
     */
    @Transactional
    public Escrow releaseCredits(Long taskId) {
        Escrow escrow = escrowRepository.findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("Escrow not found for task " + taskId));

        if (escrow.getStatus() != EscrowStatus.LOCKED) {
            throw new RuntimeException("Cannot release credits. Escrow status: " + escrow.getStatus());
        }

        // Transfer credits to bidder
        User bidder = escrow.getBidder();
        bidder.setCredits(bidder.getCredits() + escrow.getLockedCredits());
        userRepository.save(bidder);

        // Update escrow
        escrow.setReleasedCredits(escrow.getLockedCredits());
        escrow.setStatus(EscrowStatus.RELEASED);
        escrow.setResolvedAt(LocalDateTime.now());
        escrow.setResolutionNotes("Work approved by creator. Full payment released.");

        Escrow savedEscrow = escrowRepository.save(escrow);
        log.info("ESCROW RELEASED: {} credits to {} for task {}",
                escrow.getReleasedCredits(), bidder.getName(), taskId);

        return savedEscrow;
    }

    /**
     * Refund all escrowed credits to the creator.
     * Called when dispute resolves in creator's favor or task is cancelled.
     * 
     * @param taskId The task ID
     * @param reason Reason for refund
     * @return Updated escrow record
     */
    @Transactional
    public Escrow refundCredits(Long taskId, String reason) {
        Escrow escrow = escrowRepository.findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("Escrow not found for task " + taskId));

        if (escrow.getStatus() != EscrowStatus.LOCKED) {
            throw new RuntimeException("Cannot refund credits. Escrow status: " + escrow.getStatus());
        }

        // Refund credits to creator
        User creator = escrow.getCreator();
        creator.setCredits(creator.getCredits() + escrow.getLockedCredits());
        userRepository.save(creator);

        // Update escrow
        escrow.setRefundedCredits(escrow.getLockedCredits());
        escrow.setStatus(EscrowStatus.REFUNDED);
        escrow.setResolvedAt(LocalDateTime.now());
        escrow.setResolutionNotes(reason);

        Escrow savedEscrow = escrowRepository.save(escrow);
        log.info("ESCROW REFUNDED: {} credits to {} for task {} - Reason: {}",
                escrow.getRefundedCredits(), creator.getName(), taskId, reason);

        return savedEscrow;
    }

    /**
     * Partial release for dispute resolution.
     * Splits escrowed credits between creator and bidder.
     * 
     * @param taskId The task ID
     * @param bidderPercentage Percentage (0-100) to release to bidder
     * @param disputeId Associated dispute ID
     * @param notes Resolution notes
     * @return Updated escrow record
     */
    @Transactional
    public Escrow partialRelease(Long taskId, int bidderPercentage, Long disputeId, String notes) {
        if (bidderPercentage < 0 || bidderPercentage > 100) {
            throw new RuntimeException("Bidder percentage must be between 0 and 100");
        }

        Escrow escrow = escrowRepository.findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("Escrow not found for task " + taskId));

        if (escrow.getStatus() != EscrowStatus.LOCKED) {
            throw new RuntimeException("Cannot release credits. Escrow status: " + escrow.getStatus());
        }

        int totalCredits = escrow.getLockedCredits();
        int bidderAmount = (totalCredits * bidderPercentage) / 100;
        int creatorAmount = totalCredits - bidderAmount;

        // Transfer to bidder
        User bidder = escrow.getBidder();
        bidder.setCredits(bidder.getCredits() + bidderAmount);
        userRepository.save(bidder);

        // Refund to creator
        User creator = escrow.getCreator();
        creator.setCredits(creator.getCredits() + creatorAmount);
        userRepository.save(creator);

        // Update escrow
        escrow.setReleasedCredits(bidderAmount);
        escrow.setRefundedCredits(creatorAmount);
        escrow.setStatus(EscrowStatus.PARTIAL_RELEASE);
        escrow.setResolvedAt(LocalDateTime.now());
        escrow.setDisputeId(disputeId);
        escrow.setResolutionNotes(notes);

        Escrow savedEscrow = escrowRepository.save(escrow);
        log.info("ESCROW PARTIAL RELEASE: {} credits to bidder, {} credits to creator for task {} (dispute: {})",
                bidderAmount, creatorAmount, taskId, disputeId);

        return savedEscrow;
    }

    /**
     * Cancel escrow (task cancelled before work started)
     * Full refund to creator
     */
    @Transactional
    public Escrow cancelEscrow(Long taskId) {
        Escrow escrow = escrowRepository.findByTaskId(taskId)
                .orElseThrow(() -> new RuntimeException("Escrow not found for task " + taskId));

        if (escrow.getStatus() != EscrowStatus.LOCKED) {
            throw new RuntimeException("Cannot cancel escrow. Status: " + escrow.getStatus());
        }

        // Refund to creator
        User creator = escrow.getCreator();
        creator.setCredits(creator.getCredits() + escrow.getLockedCredits());
        userRepository.save(creator);

        // Update escrow
        escrow.setRefundedCredits(escrow.getLockedCredits());
        escrow.setStatus(EscrowStatus.CANCELLED);
        escrow.setResolvedAt(LocalDateTime.now());
        escrow.setResolutionNotes("Task cancelled. Full refund to creator.");

        Escrow savedEscrow = escrowRepository.save(escrow);
        log.info("ESCROW CANCELLED: {} credits refunded to {} for task {}",
                escrow.getRefundedCredits(), creator.getName(), taskId);

        return savedEscrow;
    }

    // ============================================
    // QUERY METHODS
    // ============================================

    public Optional<Escrow> getEscrowByTaskId(Long taskId) {
        return escrowRepository.findByTaskId(taskId);
    }

    public List<Escrow> getCreatorEscrows(User creator) {
        return escrowRepository.findByCreator(creator);
    }

    public List<Escrow> getBidderEscrows(User bidder) {
        return escrowRepository.findByBidder(bidder);
    }

    public List<Escrow> getCreatorLockedEscrows(User creator) {
        return escrowRepository.findByCreatorAndStatus(creator, EscrowStatus.LOCKED);
    }

    public List<Escrow> getBidderPendingEscrows(User bidder) {
        return escrowRepository.findByBidderAndStatus(bidder, EscrowStatus.LOCKED);
    }

    public int getTotalLockedCreditsForCreator(User user) {
        return escrowRepository.getTotalLockedCreditsAsCreator(user);
    }

    public int getTotalPendingEarningsForBidder(User user) {
        return escrowRepository.getTotalPendingEarningsAsBidder(user);
    }

    public boolean isEscrowLocked(Long taskId) {
        return escrowRepository.existsByTaskIdAndStatus(taskId, EscrowStatus.LOCKED);
    }
}
