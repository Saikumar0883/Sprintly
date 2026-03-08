package com.sprintly.auth.service;

import com.sprintly.user.entity.User;
import com.sprintly.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ─────────────────────────────────────────────────────────────────
 *  CustomUserDetailsService
 * ─────────────────────────────────────────────────────────────────
 *  Implements Spring Security's UserDetailsService interface.
 *
 *  Role in the authentication flow:
 *    AuthenticationManager.authenticate()
 *      → calls loadUserByUsername(email)
 *      → retrieves User from DB
 *      → Spring checks password with BCryptPasswordEncoder
 *      → if valid, populates SecurityContext
 *
 *  Why a custom implementation?
 *    Spring Security's default implementation looks up users by
 *    "username" in memory. We need to look up by email in PostgreSQL,
 *    and map our UserRole enum to a GrantedAuthority.
 *
 *  GrantedAuthority:
 *    We wrap the role string (e.g. "ROLE_DEVELOPER") in
 *    SimpleGrantedAuthority so Spring's @PreAuthorize and
 *    hasRole() checks work correctly.
 *    IMPORTANT: Spring's hasRole("ADMIN") checks for "ROLE_ADMIN",
 *    so the "ROLE_" prefix in the enum name is intentional.
 * ─────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user from the database by their email address.
     * Called automatically by Spring Security during authentication.
     *
     * @param email the email entered by the user (Spring passes it as "username")
     * @return UserDetails containing credentials and authorities
     * @throws UsernameNotFoundException if no user exists with this email
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        log.debug("Loading user by email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Authentication attempt for unknown email: {}", email);
                    // Use a generic message to avoid leaking whether the email exists
                    return new UsernameNotFoundException("Invalid credentials");
                });

        /*
         * Build Spring Security's UserDetails from our User entity.
         *
         * - username   : email (our primary identifier)
         * - password   : BCrypt-hashed password (Spring compares this)
         * - authorities: single role wrapped as GrantedAuthority
         * - enabled    : false → UsernameNotFoundException equivalent (DisabledException)
         */
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword() != null ? user.getPassword() : "")
                .authorities(List.of(new SimpleGrantedAuthority(user.getRole().name())))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }
}
