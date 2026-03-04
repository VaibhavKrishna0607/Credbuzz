package com.credbuzz.repository;

import com.credbuzz.entity.Submission;
import com.credbuzz.entity.SubmissionStatus;
import com.credbuzz.entity.Task;
import com.credbuzz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    
    List<Submission> findByTaskOrderByVersionNumberDesc(Task task);
    
    List<Submission> findByTaskIdOrderByVersionNumberDesc(Long taskId);
    
    Optional<Submission> findByTaskAndVersionNumber(Task task, Integer versionNumber);
    
    Optional<Submission> findByTaskIdAndVersionNumber(Long taskId, Integer versionNumber);
    
    List<Submission> findBySubmitter(User submitter);
    
    List<Submission> findByStatus(SubmissionStatus status);
    
    /**
     * Get the latest submission for a task
     */
    @Query("SELECT s FROM Submission s WHERE s.task.id = :taskId ORDER BY s.versionNumber DESC LIMIT 1")
    Optional<Submission> findLatestByTaskId(@Param("taskId") Long taskId);
    
    /**
     * Get the latest submission for a task (alternative query)
     */
    Optional<Submission> findFirstByTaskIdOrderByVersionNumberDesc(Long taskId);
    
    /**
     * Count submissions for a task (revision count)
     */
    long countByTaskId(Long taskId);
    
    /**
     * Check if maximum revisions reached (2 revisions = 3 total submissions)
     */
    @Query("SELECT COUNT(s) >= 3 FROM Submission s WHERE s.task.id = :taskId")
    boolean hasReachedMaxRevisions(@Param("taskId") Long taskId);
    
    /**
     * Get all submissions pending AI review
     */
    List<Submission> findByStatusOrderBySubmittedAtAsc(SubmissionStatus status);
    
    /**
     * Get average AI score for a user's submissions
     */
    @Query("SELECT AVG(s.aiFinalScore) FROM Submission s WHERE s.submitter = :user AND s.aiFinalScore IS NOT NULL")
    Double getAverageAIScoreForUser(@Param("user") User user);
}
