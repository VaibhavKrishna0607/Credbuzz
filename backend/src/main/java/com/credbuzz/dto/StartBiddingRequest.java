package com.credbuzz.dto;

import jakarta.validation.constraints.Future;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for starting the bidding phase on a task
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartBiddingRequest {

    @Future(message = "Bidding deadline must be in the future")
    private LocalDateTime biddingDeadline;

    /**
     * Maximum number of bids before auto-closing auction.
     * Default: 5 bids
     */
    private Integer maxBids = 5;
}
