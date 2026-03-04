package com.credbuzz.repository;

import com.credbuzz.entity.Dispute;
import com.credbuzz.entity.DisputeResolution;
import com.credbuzz.entity.DisputeStatus;
import com.credbuzz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, Long> {
    
    Optional<Dispute> findByTaskId(Long taskId);
    
    List<Dispute> findByFiledByOrderByCreatedAtDesc(User user);
    
    List<Dispute> findByFiledAgainstOrderByCreatedAtDesc(User user);
    
    List<Dispute> findByStatus(DisputeStatus status);
    
    List<Dispute> findByStatusOrderByFiledAtAsc(DisputeStatus status);
    
    /**
     * Get all disputes involving a user (filed by or against)
     */
    @Query("SELECT d FROM Dispute d WHERE d.filedBy = :user OR d.filedAgainst = :user ORDER BY d.filedAt DESC")
    List<Dispute> findAllInvolvingUser(@Param("user") User user);
    
    /**
     * Count disputes filed against a user (as defendant)
     */
    long countByFiledAgainst(User user);
    
    /**
     * Count resolved disputes where user was found at fault
     */
    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.filedAgainst = :user AND d.resolution = 'FULL_TO_CREATOR' AND d.status = 'RESOLVED'")
    long countResolvedAgainstUser(@Param("user") User user);
    
    /**
     * Count disputes filed by a user that were dismissed (abuse indicator)
     */
    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.filedBy = :user AND d.resolution = 'DISMISSED' AND d.status = 'RESOLVED'")
    long countDismissedByFiler(@Param("user") User user);
    
    /**
     * Get win rate for a user in disputes they filed
     */
    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.filedBy = :user AND d.resolution IN ('FULL_TO_CREATOR', 'FULL_TO_BIDDER', 'SPLIT') AND d.status = 'RESOLVED'")
    long countWinsAsFiler(@Param("user") User user);
    
    /**
     * Check if task already has an active dispute
     */
    @Query("SELECT COUNT(d) > 0 FROM Dispute d WHERE d.task.id = :taskId AND d.status NOT IN ('RESOLVED', 'CANCELLED')")
    boolean hasActiveDispute(@Param("taskId") Long taskId);
}
