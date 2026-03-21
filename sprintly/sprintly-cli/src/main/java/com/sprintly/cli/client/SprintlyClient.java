package com.sprintly.cli.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sprintly.cli.config.CliConfig;
import com.sprintly.common.dto.ApiResponse;
import org.apache.hc.client5.http.classic.methods.HttpGet;
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
 * HTTP client for communicating with the Sprintly backend REST API.
 *
 * Supports: GET, POST, PUT
 * Handles:
 *   - Bearer token attachment for authenticated requests
 *   - Empty response body (fixes MismatchedInputException)
 *   - HTML response detection (Spring Security redirect)
 *   - Connection refused errors
 *   - Login state check
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
            attachToken(post, authenticated);

            if (body != null) {
                post.setEntity(new StringEntity(
                        mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
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
            attachToken(get, authenticated);

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                return parseResponse(response, typeReference);
            } catch (java.net.ConnectException e) {
                return ApiResponse.<T>error(
                        "Cannot connect to Sprintly backend at " + BASE_URL
                                + ". Is the server running?");
            }
        }
    }

    // ── HTTP PUT ─────────────────────────────────────────────────────────────

    /**
     * PUT request — used by notification mark-as-read endpoints.
     *
     * Backend notification endpoints use PUT:
     *   PUT /notifications/{id}/read   → mark one as read
     *   PUT /notifications/read-all    → mark all as read
     *
     * @param path            API path (e.g. "/notifications/5/read")
     * @param body            Request body (null is fine for mark-as-read calls)
     * @param typeReference   Expected response type
     * @param authenticated   Whether to attach Bearer token
     */
    public <T> ApiResponse<T> put(String path, Object body,
                                  TypeReference<ApiResponse<T>> typeReference,
                                  boolean authenticated) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPut put = new HttpPut(BASE_URL + path);
            put.setHeader("Content-Type", "application/json");
            attachToken(put, authenticated);

            if (body != null) {
                put.setEntity(new StringEntity(
                        mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
            }

            try (CloseableHttpResponse response = httpClient.execute(put)) {
                return parseResponse(response, typeReference);
            } catch (java.net.ConnectException e) {
                return ApiResponse.<T>error(
                        "Cannot connect to Sprintly backend at " + BASE_URL
                                + ". Is the server running?");
            }
        }
    }

    // ── Token attachment helper ───────────────────────────────────────────────

    private void attachToken(org.apache.hc.core5.http.HttpRequest request,
                             boolean authenticated) {
        if (authenticated) {
            CliConfig config = CliConfig.load();
            if (config != null && config.getAccessToken() != null) {
                request.setHeader("Authorization", "Bearer " + config.getAccessToken());
            }
        }
    }

    // ── Shared response parser ───────────────────────────────────────────────

    /**
     * Reads response body as String first, then parses JSON.
     *
     * Fixes MismatchedInputException when server returns empty body.
     * Old code passed InputStream directly to Jackson — crashes on empty.
     * New code reads String first, checks blank, then parses safely.
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
                throw new RuntimeException(e);
            }
        }

        // Empty body
        if (body == null || body.isBlank()) {
            if (statusCode == 401) {
                return ApiResponse.<T>error("Session expired. Run: sprintly login");
            }
            if (statusCode == 403) {
                return ApiResponse.<T>error("Access denied.");
            }
            return ApiResponse.<T>error("Server returned empty response (HTTP " + statusCode + ").");
        }

        // HTML = Spring Security redirect (unauthenticated)
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