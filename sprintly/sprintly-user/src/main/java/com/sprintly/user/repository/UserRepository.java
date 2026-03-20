package com.sprintly.user.repository;

import com.sprintly.user.entity.User;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ─────────────────────────────────────────────────────────────────
 *  UserRepository
 * ─────────────────────────────────────────────────────────────────
 *  Data access interface for User entity.
 *
 *  Shared by both taskflow-auth (login lookups) and taskflow-user
 *  (profile management). Implementation uses Spring JDBC.
 *
 *  Naming convention:
 *    findBy<Field>           → returns Optional (nullable)
 *    existsBy<Field>         → returns boolean (cheap existence check)
 * ─────────────────────────────────────────────────────────────────
 */
@Repository
public interface UserRepository {

    /**
     * Find a user by their email address.
     * Used during login to load the UserDetails for authentication.
     *
     * @param email case-sensitive email string
     * @return Optional containing the user, or empty if not found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check whether an email is already registered.
     * Used during registration to prevent duplicate accounts
     * without loading the full entity.
     *
     * @param email email to check
     * @return true if a user with this email already exists
     */
    boolean existsByEmail(String email);


    /**
     * Save a new user or update an existing one.
     *
     * @param user the user entity to persist
     * @return the saved/updated user
     */
    User save(User user);

    /**
     * Find a user by ID.
     *
     * @param id the user ID
     * @return Optional containing the user, or empty if not found
     */
    Optional<User> findById(Long id);

    /**
     * Delete a user by ID.
     *
     * @param id the user ID to delete
     * @return true if a user was deleted, false if not found
     */
    boolean deleteById(Long id);

    /**
     * Find all users.
     * @return List of all users
     */
    java.util.List<User> findAll();
}
