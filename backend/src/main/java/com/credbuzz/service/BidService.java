package com.credbuzz.service;

import com.credbuzz.dto.BidDto;
import com.credbuzz.dto.BidScoreDto;
import com.credbuzz.dto.CreateBidRequest;
import com.credbuzz.entity.Bid;
import com.credbuzz.entity.Task;
import com.credbuzz.entity.TaskStatus;
import com.credbuzz.entity.User;
import com.credbuzz.repository.BidRepository;
import com.credbuzz.repository.TaskRepository;
import com.credbuzz.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ============================================
 * Bid Service - Business Logic Layer
 * ============================================
 * 
 * Handles all bid-related business logic.
 * Auto-closes auction when maxBids threshold is reached.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidService {
    /**
     * Check if the user is the poster (creator) of the task or admin (future)
     */
    @Transactional(readOnly = true)
    public boolean isTaskPosterOrAdmin(Long taskId, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElse(null);
        if (task == null) return false;
        // TODO: Add admin check if/when roles are implemented
        return task.getPoster().getId().equals(userId);
    }

    private final BidRepository bidRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final UserPerformanceService userPerformanceService;
    private final BidEvaluationService bidEvaluationService;
    private final EscrowService escrowService;
    
    @Lazy
    private final TaskService taskService;

    /**
     * Create a new bid on a task
     */
    @Transactional
    public BidDto createBid(Long taskId, Long bidderId, CreateBidRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        User bidder = userRepository.findById(bidderId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Validation: Task must be in BIDDING status
        if (task.getStatus() != TaskStatus.BIDDING) {
            throw new RuntimeException("Task is not open for bidding. Current status: " + task.getStatus());
        }

        // Validation: User cannot bid on their own task
        if (task.getPoster().getId().equals(bidderId)) {
            throw new RuntimeException("You cannot bid on your own task");
        }

        // Validation: User cannot bid twice on same task
        if (bidRepository.existsByTaskIdAndBidderId(taskId, bidderId)) {
            throw new RuntimeException("You have already placed a bid on this task");
        }

        // Validation: Proposed credits must be positive
        if (request.getProposedCredits() < 1) {
            throw new RuntimeException("Proposed credits must be at least 1");
        }

        // Create bid
        Bid bid = Bid.builder()
                .task(task)
                .bidder(bidder)
                .proposedCredits(request.getProposedCredits())
                .proposedCompletionDays(request.getProposedCompletionDays())
                .proposalMessage(request.getProposalMessage())
                .selected(false)
                .build();

        Bid savedBid = bidRepository.save(bid);
        
        // Track bid placement for user performance metrics
        userPerformanceService.recordBidPlaced(bidderId, request.getProposedCredits());
        
        // Check if we've reached maxBids threshold - notify creator for manual selection
        Integer maxBids = task.getMaxBids();
        if (maxBids != null && maxBids > 0) {
            long bidCount = bidRepository.countByTaskId(taskId);
            log.info("Task {} has {}/{} bids", taskId, bidCount, maxBids);
            
            if (bidCount >= maxBids) {
                log.info("MaxBids threshold reached for task {}. Moving to PENDING_SELECTION for manual bid selection.", taskId);
                task.setStatus(TaskStatus.PENDING_SELECTION);
                taskRepository.save(task);
            }
        }
        
        return toDto(savedBid);
    }

    /**
     * Get all bids for a task
     */
    @Transactional(readOnly = true)
    public List<BidDto> getBidsForTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        List<Bid> bids = bidRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
        return bids.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Get all bids placed by a user
     */
    @Transactional(readOnly = true)
    public List<BidDto> getMyBids(Long userId) {
        List<Bid> bids = bidRepository.findByBidderIdOrderByCreatedAtDesc(userId);
        return bids.stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Get a specific bid by ID
     */
    @Transactional(readOnly = true)
    public BidDto getBid(Long bidId) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new RuntimeException("Bid not found"));
        return toDto(bid);
    }

    /**
     * Select a bid (assign task to bidder)
     * Only task poster can select a bid
     * 
     * This is especially important for PENDING_SELECTION status where
     * the ML system couldn't confidently auto-select a winner.
     */
    @Transactional
    public BidDto selectBid(Long bidId, Long userId) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new RuntimeException("Bid not found"));

        Task task = bid.getTask();

        // Validation: Only task poster can select a bid
        if (!task.getPoster().getId().equals(userId)) {
            throw new RuntimeException("Only task poster can select a bid");
        }

        // Validation: Task must be in BIDDING, AUCTION_CLOSED, or PENDING_SELECTION status
        if (task.getStatus() != TaskStatus.BIDDING && 
            task.getStatus() != TaskStatus.AUCTION_CLOSED &&
            task.getStatus() != TaskStatus.PENDING_SELECTION) {
            throw new RuntimeException("Cannot select bid. Task status: " + task.getStatus());
        }

        // Mark bid as selected
        bid.setSelected(true);
        bidRepository.save(bid);

        // Assign task to bidder
        task.setAssignee(bid.getBidder());
        task.setCredits(bid.getProposedCredits()); // Update credits to accepted bid amount
        task.setStatus(TaskStatus.ASSIGNED);
        taskRepository.save(task);

        // Lock credits in escrow
        escrowService.lockCredits(task, bid.getBidder());

        // Track assignment
        userPerformanceService.recordTaskAssigned(bid.getBidder().getId());

        return toDto(bid);
    }

    /**
     * Update an existing bid (only if not selected)
     */
    @Transactional
    public BidDto updateBid(Long bidId, Long userId, CreateBidRequest request) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new RuntimeException("Bid not found"));

        // Validation: Only bidder can update their bid
        if (!bid.getBidder().getId().equals(userId)) {
            throw new RuntimeException("You can only update your own bid");
        }

        // Validation: Cannot update selected bid
        if (bid.getSelected()) {
            throw new RuntimeException("Cannot update a selected bid");
        }

        // Validation: Task must still be in BIDDING status
        if (bid.getTask().getStatus() != TaskStatus.BIDDING) {
            throw new RuntimeException("Bidding phase has ended");
        }

        // Update bid
        bid.setProposedCredits(request.getProposedCredits());
        bid.setProposedCompletionDays(request.getProposedCompletionDays());
        bid.setProposalMessage(request.getProposalMessage());

        Bid savedBid = bidRepository.save(bid);
        return toDto(savedBid);
    }

    /**
     * Delete/withdraw a bid (only if not selected)
     */
    @Transactional
    public void deleteBid(Long bidId, Long userId) {
        Bid bid = bidRepository.findById(bidId)
                .orElseThrow(() -> new RuntimeException("Bid not found"));

        // Validation: Only bidder can delete their bid
        if (!bid.getBidder().getId().equals(userId)) {
            throw new RuntimeException("You can only withdraw your own bid");
        }

        // Validation: Cannot delete selected bid
        if (bid.getSelected()) {
            throw new RuntimeException("Cannot withdraw a selected bid");
        }

        bidRepository.delete(bid);
    }

    /**
     * Get bid count for a task
     */
    public long getBidCount(Long taskId) {
        return bidRepository.countByTaskId(taskId);
    }

    /**
     * Get ranked bids for a task using AI scoring
     */
    @Transactional(readOnly = true)
    public List<BidScoreDto> getRankedBids(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return bidEvaluationService.evaluateAndRankBids(task);
    }

    /**
     * Convert Bid entity to DTO
     */
    private BidDto toDto(Bid bid) {
        return BidDto.builder()
                .id(bid.getId())
                .taskId(bid.getTask().getId())
                .taskTitle(bid.getTask().getTitle())
                .bidder(userService.toDto(bid.getBidder()))
                .proposedCredits(bid.getProposedCredits())
                .proposedCompletionDays(bid.getProposedCompletionDays())
                .proposalMessage(bid.getProposalMessage())
                .selected(bid.getSelected())
                .createdAt(bid.getCreatedAt())
                .build();
    }
}
