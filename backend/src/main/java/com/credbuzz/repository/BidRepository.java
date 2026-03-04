package com.credbuzz.repository;

import com.credbuzz.entity.Bid;
import com.credbuzz.entity.Task;
import com.credbuzz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ============================================
 * Bid Repository - Data Access Layer
 * ============================================
 */
@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {

    /**
     * Find all bids for a specific task
     */
    List<Bid> findByTaskOrderByCreatedAtDesc(Task task);

    /**
     * Find bids by task ID
     */
    List<Bid> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    /**
     * Find all bids by a specific bidder
     */
    List<Bid> findByBidderOrderByCreatedAtDesc(User bidder);

    /**
     * Find bids by bidder ID
     */
    List<Bid> findByBidderIdOrderByCreatedAtDesc(Long bidderId);

    /**
     * Check if a user has already bid on a task
     */
    boolean existsByTaskIdAndBidderId(Long taskId, Long bidderId);

    /**
     * Find a specific bid by task and bidder
     */
    Optional<Bid> findByTaskIdAndBidderId(Long taskId, Long bidderId);

    /**
     * Find the selected bid for a task
     */
    Optional<Bid> findByTaskIdAndSelectedTrue(Long taskId);
    
    /**
     * Find bid by task and selection status
     */
    Optional<Bid> findByTaskAndSelected(Task task, Boolean selected);

    /**
     * Count bids for a task
     */
    long countByTaskId(Long taskId);

    /**
     * Find bids with the lowest proposed credits for a task
     */
    @Query("SELECT b FROM Bid b WHERE b.task.id = :taskId ORDER BY b.proposedCredits ASC")
    List<Bid> findByTaskIdOrderByProposedCreditsAsc(@Param("taskId") Long taskId);
}
