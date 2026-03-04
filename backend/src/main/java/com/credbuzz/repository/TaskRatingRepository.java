package com.credbuzz.repository;

import com.credbuzz.entity.TaskRating;
import com.credbuzz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRatingRepository extends JpaRepository<TaskRating, Long> {
    
    Optional<TaskRating> findByTaskId(Long taskId);
    
    List<TaskRating> findByRatedUser(User user);
    
    List<TaskRating> findByRatedUserOrderByCreatedAtDesc(User user);
    
    List<TaskRating> findByRater(User user);
    
    List<TaskRating> findByFlaggedForReviewTrue();
    
    /**
     * Get average quality score for a user
     */
    @Query("SELECT AVG(r.qualityScore) FROM TaskRating r WHERE r.ratedUser = :user")
    Double getAverageQualityScore(@Param("user") User user);
    
    /**
     * Get average communication score for a user
     */
    @Query("SELECT AVG(r.communicationScore) FROM TaskRating r WHERE r.ratedUser = :user")
    Double getAverageCommunicationScore(@Param("user") User user);
    
    /**
     * Get average professionalism score for a user
     */
    @Query("SELECT AVG(r.professionalismScore) FROM TaskRating r WHERE r.ratedUser = :user")
    Double getAverageProfessionalismScore(@Param("user") User user);
    
    /**
     * Get overall average rating for a user
     */
    @Query("SELECT AVG(r.averageScore) FROM TaskRating r WHERE r.ratedUser = :user")
    Double getOverallAverageRating(@Param("user") User user);
    
    /**
     * Count ratings for a user
     */
    long countByRatedUser(User user);
    
    /**
     * Get average rating given by a specific creator (for abuse detection)
     */
    @Query("SELECT AVG(r.averageScore) FROM TaskRating r WHERE r.rater = :rater")
    Double getAverageRatingGivenByRater(@Param("rater") User rater);
    
    /**
     * Count flagged ratings for a specific rater (abuse detection)
     */
    long countByRaterAndFlaggedForReviewTrue(User rater);
    
    /**
     * Check if task already has a rating
     */
    boolean existsByTaskId(Long taskId);
}
