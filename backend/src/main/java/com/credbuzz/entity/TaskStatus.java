package com.credbuzz.entity;

/**
 * ============================================
 * LEARNING NOTE: Enum for Task Status
 * ============================================
 * 
 * In MongoDB, you might use a String with validation.
 * In Java, enums are type-safe and prevent invalid values.
 */
public enum TaskStatus {
    OPEN,
    IN_PROGRESS,
    SUBMITTED,
    COMPLETED,
    CANCELLED
}
