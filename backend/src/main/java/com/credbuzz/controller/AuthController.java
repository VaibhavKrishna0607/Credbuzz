package com.credbuzz.controller;

import com.credbuzz.dto.*;
import com.credbuzz.entity.User;
import com.credbuzz.security.JwtService;
import com.credbuzz.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * ============================================
 * LEARNING NOTE: Controller Layer
 * ============================================
 * 
 * Controllers handle HTTP requests and responses.
 * This is like your Express routes + controller combined.
 * 
 * COMPARISON WITH EXPRESS:
 * -----------------------------------------
 * Express:
 * router.post('/register', register);
 * exports.register = (req, res) => { ... }
 * 
 * Spring:
 * @PostMapping("/register")
 * public ResponseEntity<?> register(@RequestBody RegisterRequest request) { ... }
 * -----------------------------------------
 * 
 * ANNOTATIONS:
 * @RestController - Combines @Controller and @ResponseBody
 *                   Returns JSON automatically (no need for res.json())
 * @RequestMapping("/api/auth") - Base path for all endpoints in this controller
 * @PostMapping, @GetMapping, etc. - HTTP method mapping
 * @RequestBody - Parse JSON body (like express.json() middleware)
 * @Valid - Trigger validation on the request DTO
 */                                                 
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Register a new user
     * 
     * POST /api/auth/register
     * 
     * Request body: { name, email, password }
     * Response: { success, token, user }
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            // Register user
            User user = userService.register(request);
            
            // Generate JWT token
            String token = jwtService.generateToken(user);
            
            // Convert to DTO and return response
            UserDto userDto = userService.toDto(user);
            return ResponseEntity.ok(AuthResponse.success(token, userDto));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(AuthResponse.error(e.getMessage()));
        }
    }

    /**
     * Login user
     * 
     * POST /api/auth/login
     * 
     * Request body: { email, password }
     * Response: { success, token, user }
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
            
            // Get authenticated user
            User user = userService.findByEmail(request.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Generate JWT token
            String token = jwtService.generateToken(user);
            
            // Return response
            UserDto userDto = userService.toDto(user);
            return ResponseEntity.ok(AuthResponse.success(token, userDto));
            
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(AuthResponse.error("Invalid credentials"));
        }
    }

    /**
     * Get current logged-in user
     * 
     * GET /api/auth/me
     * 
     * Headers: Authorization: Bearer <token>
     * Response: { success, user }
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getMe() {
        try {
            // Get current user from security context
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String email = auth.getName();
            
            User user = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            UserDto userDto = userService.toDto(user);
            return ResponseEntity.ok(AuthResponse.builder()
                    .success(true)
                    .user(userDto)
                    .build());
            
        } catch (Exception e) {
            return ResponseEntity.status(401)
                    .body(AuthResponse.error(e.getMessage()));
        }
    }

    // ============================================
    // TODO: IMPLEMENT THESE ENDPOINTS
    // ============================================
    
    /**
     * TODO: Google OAuth Login
     * POST /api/auth/google
     * 
     * @PostMapping("/google")
     * public ResponseEntity<AuthResponse> googleLogin(@RequestBody GoogleLoginRequest request) {
     *     // 1. Verify Google access token
     *     // 2. Extract user info
     *     // 3. Find or create user
     *     // 4. Generate JWT
     *     // 5. Return response
     * }
     */
    
    /**
     * TODO: Request Password Reset OTP
     * POST /api/auth/request-otp
     * 
     * @PostMapping("/request-otp")
     * public ResponseEntity<ApiResponse<String>> requestOTP(@RequestBody OTPRequest request) {
     *     // 1. Verify email exists
     *     // 2. Generate OTP
     *     // 3. Send email
     *     // 4. Return success
     * }
     */
    
    /**
     * TODO: Reset Password with OTP
     * POST /api/auth/reset-password
     * 
     * @PostMapping("/reset-password")
     * public ResponseEntity<ApiResponse<String>> resetPassword(@RequestBody ResetPasswordRequest request) {
     *     // 1. Verify OTP
     *     // 2. Update password
     *     // 3. Return success
     * }
     */
}
