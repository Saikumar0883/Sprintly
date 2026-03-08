package com.sprintly.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * ─────────────────────────────────────────────────────────────────
 *  JwtService
 * ─────────────────────────────────────────────────────────────────
 *  Handles all JWT operations: generation, parsing, and validation.
 *
 *  Token anatomy:
 *    Header  : { "alg": "HS256", "typ": "JWT" }
 *    Payload : { "sub": "email", "role": "ROLE_DEVELOPER",
 *                "userId": 1, "iat": ..., "exp": ... }
 *    Signature: HMAC-SHA256(base64(header) + "." + base64(payload), secret)
 *
 *  Two token types:
 *    Access Token  → short-lived (15 min), carries claims, sent on every request
 *    Refresh Token → long-lived (7 days), stored in DB, only used on /refresh
 *
 *  Security:
 *    - Uses HS256 with a 256-bit (32 char min) secret key
 *    - Secret injected from application.yml / environment variable
 *    - Never expose the secret in source code or logs
 *
 *  Design Pattern: Service (stateless utility, Spring-managed singleton bean)
 * ─────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
public class JwtService {

    // ── Configuration (injected from application.yml) ──────────────

    /** HMAC-SHA256 secret key — must be at least 32 characters */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Access token expiry in milliseconds (default: 15 minutes) */
    @Value("${jwt.access-token-expiry-ms:900000}")
    private long accessTokenExpiryMs;

    /** Refresh token expiry in milliseconds (default: 7 days) */
    @Value("${jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    // ── Token Generation ────────────────────────────────────────────

    /**
     * Generates a signed JWT access token for the given user.
     *
     * Extra claims embedded in the token:
     *   - userId : allows the receiver to identify the user without a DB call
     *   - role   : allows @PreAuthorize to work without loading the user
     *
     * @param userDetails Spring Security UserDetails (email = username)
     * @param userId      database ID of the user
     * @param role        user's application role string
     * @return signed JWT string
     */
    public String generateAccessToken(UserDetails userDetails, Long userId, String role) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", userId);
        extraClaims.put("role", role);
        extraClaims.put("type", "access");

        return buildToken(extraClaims, userDetails.getUsername(), accessTokenExpiryMs);
    }

    /**
     * Generates a signed JWT refresh token.
     * Refresh tokens carry minimal claims — they only identify the subject.
     * The full user record is loaded from DB during refresh validation.
     *
     * @param userDetails Spring Security UserDetails
     * @return signed JWT refresh token string
     */
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");

        return buildToken(claims, userDetails.getUsername(), refreshTokenExpiryMs);
    }

    /**
     * Core token builder — shared by access and refresh token generation.
     *
     * @param extraClaims additional payload fields
     * @param subject     the JWT "sub" claim (user email)
     * @param expiryMs    token lifetime in milliseconds
     * @return signed compact JWT string
     */
    private String buildToken(Map<String, Object> extraClaims, String subject, long expiryMs) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .claims(extraClaims)        // custom payload fields
                .subject(subject)           // identifies the token owner
                .issuedAt(new Date(now))    // when the token was created
                .expiration(new Date(now + expiryMs))  // absolute expiry
                .signWith(getSigningKey())  // HMAC-SHA256 signature
                .compact();                 // serialize to "xxxxx.yyyyy.zzzzz"
    }

    // ── Token Validation ────────────────────────────────────────────

    /**
     * Validates a token against the given UserDetails.
     * Checks:
     *   1. Token signature is valid (not tampered)
     *   2. Token is not expired
     *   3. Token subject (email) matches the provided UserDetails
     *
     * @param token       JWT string from Authorization header
     * @param userDetails the user loaded from DB
     * @return true if the token is authentic and belongs to this user
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String subject = extractSubject(token);
            return subject.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Quick signature + expiry check without needing UserDetails.
     * Used in JwtAuthFilter to reject obviously bad tokens early.
     *
     * @param token JWT string
     * @return true if the token parses and is not expired
     */
    public boolean isTokenStructureValid(String token) {
        try {
            extractAllClaims(token); // throws if signature is invalid
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token structure invalid: {}", e.getMessage());
            return false;
        }
    }

    // ── Claims Extraction ───────────────────────────────────────────

    /**
     * Extracts the subject (email) from the token.
     * Used by JwtAuthFilter to identify which user made the request.
     */
    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the userId embedded in the token claims.
     * Avoids a DB roundtrip just to get the user's ID.
     */
    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", Long.class);
    }

    /**
     * Extracts the role embedded in the token claims.
     */
    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    /**
     * Generic claim extractor using a resolver function.
     * Allows callers to extract any claim without exposing raw Claims.
     *
     * @param token    JWT string
     * @param resolver function that picks a field from Claims
     * @param <T>      type of the extracted claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    // ── Private Helpers ─────────────────────────────────────────────

    /**
     * Parses and returns all claims from the token.
     * This performs signature verification — throws JwtException on failure.
     *
     * @param token JWT string
     * @return Claims object containing all payload fields
     * @throws JwtException if signature is invalid or token is malformed
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())   // verify HMAC-SHA256 signature
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Checks whether the token's expiry date is before the current time.
     */
    private boolean isTokenExpired(String token) {
        Date expiry = extractClaim(token, Claims::getExpiration);
        return expiry.before(new Date());
    }

    /**
     * Derives the HMAC-SHA256 signing key from the configured secret string.
     * The secret must be at least 32 ASCII characters (256 bits) for HS256.
     *
     * Called on every token generation and verification — always computed
     * fresh (cheap operation; Keys.hmacShaKeyFor is fast).
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ── Getters for expiry config (used in AuthService) ─────────────

    public long getAccessTokenExpiryMs() {
        return accessTokenExpiryMs;
    }

    public long getRefreshTokenExpiryMs() {
        return refreshTokenExpiryMs;
    }
}
