package com.credbuzz.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * ============================================
 * Bid Entity - Auction Bidding System
 * ============================================
 * 
 * Represents a bid placed by a user on a task.
 * Users can propose their credits and completion time.
 */
@Entity
@Table(name = "bids", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"task_id", "bidder_id"}, name = "uk_bid_task_bidder")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bidder_id", nullable = false)
    private User bidder;

    @Column(nullable = false)
    private Integer proposedCredits;

    @Column(nullable = false)
    private Integer proposedCompletionDays;

    @Column(length = 2000)
    private String proposalMessage;

    @Column(nullable = false)
    @Builder.Default
    private Boolean selected = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
