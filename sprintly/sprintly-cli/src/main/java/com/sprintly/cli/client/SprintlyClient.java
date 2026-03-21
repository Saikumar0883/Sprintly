package com.sprintly.cli.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.common.dto.ApiResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;

/**
 * HTTP client for communicating with the Sprintly backend REST API.
 *
 * ROOT CAUSE FIX for MismatchedInputException:
 *   Old code passed InputStream directly to Jackson — crashes when body is empty.
 *   New code reads body as String first, checks if blank, then parses.
 *   Jackson never sees an empty stream.
 */
public class SprintlyClient {

    private static final String BASE_URL = "http://localhost:8080/api";

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ── Login check ──────────────────────────────────────────────────────────

    /**
     * Returns true if a saved access token exists in ~/.sprintly-cli.json
     */
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

    // ── HTTP POST ────────────────────────────────────────────────────────────

    public <T> ApiResponse<T> post(String path, Object body,
                                   TypeReference<ApiResponse<T>> typeReference,
                                   boolean authenticated) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost post = new HttpPost(BASE_URL + path);
            post.setHeader("Content-Type", "application/json");

            if (authenticated) {
                CliConfig config = CliConfig.load();
                if (config != null && config.getAccessToken() != null) {
                    post.setHeader("Authorization", "Bearer " + config.getAccessToken());
                }
            }

            if (body != null) {
                String json = mapper.writeValueAsString(body);
                post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            }

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                return parseResponse(response, typeReference);
            } catch (java.net.ConnectException e) {
                return ApiResponse.<T>error(
                        "Cannot connect to Sprintly backend at " + BASE_URL
                                + ". Is the server running?");
            }
        }
    }

    // ── HTTP GET ─────────────────────────────────────────────────────────────

    public <T> ApiResponse<T> get(String path,
                                  TypeReference<ApiResponse<T>> typeReference,
                                  boolean authenticated) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpGet get = new HttpGet(BASE_URL + path);

            if (authenticated) {
                CliConfig config = CliConfig.load();
                if (config != null && config.getAccessToken() != null) {
                    get.setHeader("Authorization", "Bearer " + config.getAccessToken());
                }
            }

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                return parseResponse(response, typeReference);
            } catch (java.net.ConnectException e) {
                return ApiResponse.<T>error(
                        "Cannot connect to Sprintly backend at " + BASE_URL
                                + ". Is the server running?");
            }
        }
    }

    // ── Shared response parser ───────────────────────────────────────────────

    /**
     * Reads response body as String first, then parses.
     *
     * This is the fix for:
     *   MismatchedInputException: No content to map due to end-of-input
     *   at (org.apache.hc.core5.http.io.entity.EmptyInputStream)
     *
     * Root cause: the backend returned an empty body (HTTP 401/403/204).
     * Old code: mapper.readValue(response.getEntity().getContent(), ...)
     *           → Jackson gets an empty InputStream → crashes
     * New code: read body as String first → check if blank → parse safely
     */
    private <T> ApiResponse<T> parseResponse(CloseableHttpResponse response,
                                             TypeReference<ApiResponse<T>> typeReference)
            throws IOException {

        int statusCode = response.getCode();

        // Read entire body as String (handles null entity + empty body safely)
        String body = null;
        if (response.getEntity() != null) {
            try {
                body = EntityUtils.toString(response.getEntity(), "UTF-8");
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        // Empty body — happens on 401/403 without body, or 204 No Content
        if (body == null || body.isBlank()) {
            if (statusCode == 401) {
                return ApiResponse.<T>error("Session expired. Run: sprintly login");
            }
            if (statusCode == 403) {
                return ApiResponse.<T>error("Access denied.");
            }
            return ApiResponse.<T>error("Server returned empty response (HTTP " + statusCode + ").");
        }

        // HTML body = Spring Security redirect (unauthenticated)
        if (body.trim().startsWith("<")) {
            return ApiResponse.<T>error("Not authenticated. Run: sprintly login");
        }

        // Normal JSON parse
        try {
            return mapper.readValue(body, typeReference);
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            return ApiResponse.<T>error(
                    "Unexpected server response (HTTP " + statusCode + "): "
                            + body.substring(0, Math.min(body.length(), 120)));
        }
    }
}
