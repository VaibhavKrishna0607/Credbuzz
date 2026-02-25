package com.credbuzz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Bid responses
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidDto {
    private Long id;
    private Long taskId;
    private String taskTitle;
    private UserDto bidder;
    private Integer proposedCredits;
    private Integer proposedCompletionDays;
    private String proposalMessage;
    private Boolean selected;
    private LocalDateTime createdAt;
}
