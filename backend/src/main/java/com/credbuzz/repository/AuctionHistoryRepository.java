package com.credbuzz.repository;

import com.credbuzz.entity.AuctionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AuctionHistory entity
 * Used for ML dataset export and analytics
 */
@Repository
public interface AuctionHistoryRepository extends JpaRepository<AuctionHistory, Long> {

    List<AuctionHistory> findByTaskId(Long taskId);

    List<AuctionHistory> findByBidderId(Long bidderId);

    List<AuctionHistory> findByWasSelectedTrue();

    List<AuctionHistory> findByCompletedSuccessfullyTrue();

    @Query("SELECT ah FROM AuctionHistory ah WHERE ah.auctionClosedAt >= :startDate")
    List<AuctionHistory> findByAuctionClosedAfter(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT ah FROM AuctionHistory ah WHERE ah.completedSuccessfully IS NOT NULL")
    List<AuctionHistory> findCompletedAuctions();

    // For ML dataset - get all records with outcomes
    @Query("SELECT ah FROM AuctionHistory ah WHERE ah.wasSelected = true AND ah.completedSuccessfully IS NOT NULL")
    List<AuctionHistory> findTrainingData();

    // Get average metrics for similar tasks (by category)
    @Query("SELECT AVG(ah.actualCompletionDays) FROM AuctionHistory ah " +
           "WHERE ah.taskCategory = :category AND ah.completedSuccessfully = true")
    Double getAvgCompletionDaysByCategory(@Param("category") String category);

    @Query("SELECT AVG(ah.proposedCredits) FROM AuctionHistory ah " +
           "WHERE ah.taskCategory = :category")
    Double getAvgCreditsByCategory(@Param("category") String category);

    // Count for statistics
    long countByWasSelectedTrue();
    
    long countByCompletedSuccessfullyTrue();
}
