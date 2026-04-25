package com.sprintly.cli.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.common.dto.ApiResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;

/**
 * HTTP client for the Sprintly backend REST API.
 * <p>
 * Key behaviours:
 * - isLoggedIn()  → checks local config file (no network call)
 * - parseResponse() → reads body as String first (fixes MismatchedInputException)
 * - 401 errors    → "Session expired. Run: refresh" (token expired)
 * - 403 errors    → "Access denied." (wrong permissions, e.g. not assignee)
 * - HTML body     → "Not authenticated. Run: login"
 */
public class SprintlyClient {

    private static final String BASE_URL = "http://localhost:8080/api";

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ── Login check ───────────────────────────────────────────────────────────

    public boolean isLoggedIn() {
        try {
            CliConfig config = CliConfig.load();
            return config != null
                    && config.getAccessToken() != null
                    && !config.getAccessToken().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    // ── HTTP GET ──────────────────────────────────────────────────────────────

    public <T> ApiResponse<T> get(String path,
                                  TypeReference<ApiResponse<T>> typeReference,
                                  boolean authenticated) throws IOException {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpGet req = new HttpGet(BASE_URL + path);
            attachToken(req, authenticated);
            try (CloseableHttpResponse res = http.execute(req)) {
                return parseResponse(res, typeReference);
            } catch (java.net.ConnectException e) {
                return connectionError();
            }
        }
    }

    // ── HTTP POST ─────────────────────────────────────────────────────────────

    public <T> ApiResponse<T> post(String path, Object body,
                                   TypeReference<ApiResponse<T>> typeReference,
                                   boolean authenticated) throws IOException {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpPost req = new HttpPost(BASE_URL + path);
            req.setHeader("Content-Type", "application/json");
            attachToken(req, authenticated);
            if (body != null) req.setEntity(jsonEntity(body));
            try (CloseableHttpResponse res = http.execute(req)) {
                return parseResponse(res, typeReference);
            } catch (java.net.ConnectException e) {
                return connectionError();
            }
        }
    }

    // ── HTTP PUT ──────────────────────────────────────────────────────────────

    public <T> ApiResponse<T> put(String path, Object body,
                                  TypeReference<ApiResponse<T>> typeReference,
                                  boolean authenticated) throws IOException {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpPut req = new HttpPut(BASE_URL + path);
            req.setHeader("Content-Type", "application/json");
            attachToken(req, authenticated);
            if (body != null) req.setEntity(jsonEntity(body));
            try (CloseableHttpResponse res = http.execute(req)) {
                return parseResponse(res, typeReference);
            } catch (java.net.ConnectException e) {
                return connectionError();
            }
        }
    }

    // ── HTTP PATCH ────────────────────────────────────────────────────────────

    public <T> ApiResponse<T> patch(String path, Object body,
                                    TypeReference<ApiResponse<T>> typeReference,
                                    boolean authenticated) throws IOException {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpPatch req = new HttpPatch(BASE_URL + path);
            req.setHeader("Content-Type", "application/json");
            attachToken(req, authenticated);
            if (body != null) req.setEntity(jsonEntity(body));
            try (CloseableHttpResponse res = http.execute(req)) {
                return parseResponse(res, typeReference);
            } catch (java.net.ConnectException e) {
                return connectionError();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void attachToken(org.apache.hc.core5.http.HttpRequest req, boolean authenticated) {
        if (authenticated) {
            CliConfig cfg = CliConfig.load();
            if (cfg != null && cfg.getAccessToken() != null) {
                req.setHeader("Authorization", "Bearer " + cfg.getAccessToken());
            }
        }
    }

    private StringEntity jsonEntity(Object body) throws IOException {
        return new StringEntity(mapper.writeValueAsString(body), ContentType.APPLICATION_JSON);
    }

    private <T> ApiResponse<T> connectionError() {
        return ApiResponse.<T>error(
                "Cannot connect to Sprintly backend at " + BASE_URL
                        + ". Is the backend running? (cd sprintly-gateway && mvn spring-boot:run)");
    }

    /**
     * Reads the HTTP response body as a String before parsing JSON.
     * <p>
     * Why:
     * Passing InputStream directly to Jackson crashes with
     * MismatchedInputException when the body is empty (401/403/204).
     * Reading as String first lets us handle each case cleanly.
     * <p>
     * Error message improvements for fresh sessions:
     * 401 → "Session expired. Run: refresh  (or logout + login)"
     * This is the most common case after a fresh terminal start —
     * the saved token is still in ~/.sprintly-cli.json but it has
     * expired. isLoggedIn() returns true (file exists) but the
     * backend rejects it. The user needs to run 'refresh'.
     * <p>
     * 403 → "Access denied: [server message]"
     * This means the request reached a valid endpoint but the user
     * doesn't have permission (e.g. not the assignee of a task).
     */
    private <T> ApiResponse<T> parseResponse(CloseableHttpResponse response,
                                             TypeReference<ApiResponse<T>> typeReference)
            throws IOException {

        int statusCode = response.getCode();

        String body = null;
        if (response.getEntity() != null) {
            try {
                body = EntityUtils.toString(response.getEntity(), "UTF-8");
            } catch (ParseException e) {
                throw new IOException("Failed to read response body", e);
            }
        }

        // Empty body
        if (body == null || body.isBlank()) {
            if (statusCode == 401) {
                return ApiResponse.<T>error(
                        "Session expired. Run:  refresh\n" +
                                "     Or:               logout  then  login");
            }
            if (statusCode == 403) {
                return ApiResponse.<T>error("Access denied.");
            }
            if (statusCode == 204) {
                // No Content — treated as success with null data
                return ApiResponse.<T>builder().success(true).message("Done").build();
            }
            return ApiResponse.<T>error("Server returned empty response (HTTP " + statusCode + ").");
        }

        // HTML response = Spring Security redirect (token expired/invalid)
        if (body.trim().startsWith("<")) {
            return ApiResponse.<T>error(
                    "Session expired or not authenticated.\n" +
                            "     Run:  refresh   (to get a new token)\n" +
                            "     Or:   logout → login");
        }

        // Try to parse JSON
        try {
            ApiResponse<T> parsed = mapper.readValue(body, typeReference);
            // If the parsed response says not-success, attach a hint for 401
            if (!parsed.isSuccess() && statusCode == 401) {
                return ApiResponse.<T>error(
                        parsed.getMessage() + "\n" +
                                "     Run:  refresh   (to get a new token)");
            }
            return parsed;
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            return ApiResponse.<T>error(
                    "Unexpected server response (HTTP " + statusCode + "): "
                            + body.substring(0, Math.min(body.length(), 120)));
        }
    }
}