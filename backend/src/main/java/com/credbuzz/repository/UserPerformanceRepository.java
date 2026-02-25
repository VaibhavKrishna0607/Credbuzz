package com.credbuzz.repository;

import com.credbuzz.entity.UserPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UserPerformance entity
 */
@Repository
public interface UserPerformanceRepository extends JpaRepository<UserPerformance, Long> {

    Optional<UserPerformance> findByUserId(Long userId);

    @Query("SELECT up FROM UserPerformance up WHERE up.userId IN :userIds")
    List<UserPerformance> findByUserIdIn(@Param("userIds") List<Long> userIds);

    @Query("SELECT up FROM UserPerformance up ORDER BY " +
           "(CAST(up.tasksCompleted AS double) / NULLIF(up.tasksAssigned, 0)) DESC")
    List<UserPerformance> findTopPerformers();

    @Query("SELECT up FROM UserPerformance up WHERE up.avgRating >= :minRating")
    List<UserPerformance> findByMinRating(@Param("minRating") Double minRating);
}
