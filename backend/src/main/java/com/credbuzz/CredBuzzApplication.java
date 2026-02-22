package com.credbuzz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ============================================
 * LEARNING NOTE: Main Application Class
 * ============================================
 * 
 * This is the entry point of your Spring Boot application.
 * Equivalent to server.js in Express.
 * 
 * @SpringBootApplication combines:
 * - @Configuration: This class provides beans
 * - @EnableAutoConfiguration: Auto-configure based on dependencies
 * - @ComponentScan: Scan for components in this package and sub-packages
 */
@SpringBootApplication
public class CredBuzzApplication {

    public static void main(String[] args) {
        SpringApplication.run(CredBuzzApplication.class, args);
        System.out.println("🚀 CredBuzz API running on http://localhost:8080");
    }
}
