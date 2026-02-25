package com.credbuzz.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new bid
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBidRequest {

    @NotNull(message = "Proposed credits is required")
    @Min(value = 1, message = "Proposed credits must be at least 1")
    private Integer proposedCredits;

    @NotNull(message = "Proposed completion days is required")
    @Min(value = 1, message = "Proposed completion days must be at least 1")
    private Integer proposedCompletionDays;

    @Size(max = 2000, message = "Proposal message must be less than 2000 characters")
    private String proposalMessage;
}
