package com.credbuzz.repository;

import com.credbuzz.entity.Escrow;
import com.credbuzz.entity.EscrowStatus;
import com.credbuzz.entity.Task;
import com.credbuzz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EscrowRepository extends JpaRepository<Escrow, Long> {
    
    Optional<Escrow> findByTask(Task task);
    
    Optional<Escrow> findByTaskId(Long taskId);
    
    List<Escrow> findByCreator(User creator);
    
    List<Escrow> findByBidder(User bidder);
    
    List<Escrow> findByStatus(EscrowStatus status);
    
    List<Escrow> findByCreatorAndStatus(User creator, EscrowStatus status);
    
    List<Escrow> findByBidderAndStatus(User bidder, EscrowStatus status);
    
    /**
     * Get total locked credits for a user (as creator)
     */
    @Query("SELECT COALESCE(SUM(e.lockedCredits), 0) FROM Escrow e WHERE e.creator = :user AND e.status = 'LOCKED'")
    Integer getTotalLockedCreditsAsCreator(@Param("user") User user);
    
    /**
     * Get total pending earnings for a user (as bidder)
     */
    @Query("SELECT COALESCE(SUM(e.lockedCredits), 0) FROM Escrow e WHERE e.bidder = :user AND e.status = 'LOCKED'")
    Integer getTotalPendingEarningsAsBidder(@Param("user") User user);
    
    /**
     * Check if escrow exists and is in locked state
     */
    boolean existsByTaskIdAndStatus(Long taskId, EscrowStatus status);
}
