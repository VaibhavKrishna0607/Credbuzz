package com.credbuzz.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Request DTO for creating a task
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {
    
    @NotBlank(message = "Title is required")
    private String title;
    
    private String description;
    
    private String category;
    
    @NotNull(message = "Credits amount is required")
    @Min(value = 1, message = "Credits must be at least 1")
    private Integer credits;
    
    private LocalDateTime deadline;
    
    private List<String> skills;
}
