package com.credbuzz.repository;

import com.credbuzz.entity.Task;
import com.credbuzz.entity.TaskStatus;
import com.credbuzz.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * ============================================
 * LEARNING NOTE: Task Repository
 * ============================================
 * 
 * Custom query methods for tasks.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    
    /**
     * Find tasks by status
     */
    List<Task> findByStatus(TaskStatus status);
    
    /**
     * Find tasks by poster
     */
    List<Task> findByPoster(User poster);
    
    /**
     * Find tasks by assignee
     */
    List<Task> findByAssignee(User assignee);
    
    /**
     * Find tasks by poster ID
     */
    List<Task> findByPosterId(Long posterId);
    
    /**
     * Find tasks by assignee ID
     */
    List<Task> findByAssigneeId(Long assigneeId);
    
    /**
     * Find available tasks (OPEN status)
     */
    List<Task> findByStatusOrderByCreatedAtDesc(TaskStatus status);
    
    /**
     * Find tasks available for marketplace (OPEN or BIDDING)
     */
    List<Task> findByStatusInOrderByCreatedAtDesc(List<TaskStatus> statuses);
    
    /**
     * Search tasks by title or description
     */
    @Query("SELECT t FROM Task t WHERE " +
           "LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Task> searchTasks(@Param("search") String search);
    
    /**
     * Find tasks with multiple filters
     */
    @Query("SELECT t FROM Task t WHERE " +
           "(:status IS NULL OR t.status = :status) AND " +
           "(:category IS NULL OR t.category = :category) AND " +
           "(:posterId IS NULL OR t.poster.id = :posterId)")
    List<Task> findWithFilters(
            @Param("status") TaskStatus status,
            @Param("category") String category,
            @Param("posterId") Long posterId
    );

    /**
     * Find all tasks related to a user (posted or assigned)
     */
    @Query("SELECT t FROM Task t WHERE t.poster.id = :userId OR t.assignee.id = :userId ORDER BY t.createdAt DESC")
    List<Task> findByUserIdPostedOrAssigned(@Param("userId") Long userId);
    
    /**
     * Count tasks by assignee and status
     */
    long countByAssigneeAndStatus(User assignee, TaskStatus status);
    
    /**
     * Count tasks by assignee with any of the given statuses
     */
    long countByAssigneeAndStatusIn(User assignee, List<TaskStatus> statuses);
    
    /**
     * Count tasks posted by user
     */
    long countByPoster(User poster);
    
    /**
     * Count completed tasks assigned to user
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.assignee = :user AND t.status = 'COMPLETED'")
    long countCompletedByAssignee(@Param("user") User user);
}
