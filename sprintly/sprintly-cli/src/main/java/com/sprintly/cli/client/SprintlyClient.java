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
 * HTTP client for communicating with the Sprintly backend REST API.
 *
 * Supported methods: GET, POST, PUT, PATCH
 *
 * Key design:
 *   - parseResponse() reads body as String FIRST before Jackson parsing
 *     → fixes MismatchedInputException on empty bodies (401/403/204)
 *   - isLoggedIn() checks local config only — no network call
 *   - attachToken() shared helper to avoid duplicate token logic
 */
public class SprintlyClient {

    private static final String BASE_URL = "http://localhost:8080/api";

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ── Login check ──────────────────────────────────────────────────────────

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
                return connectionError();
            }
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
                return connectionError();
            }
        }
    }

    // ── HTTP PUT ─────────────────────────────────────────────────────────────

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
                return connectionError();
            }
        }
    }

    // ── HTTP PATCH ───────────────────────────────────────────────────────────

    /**
     * PATCH request — used for partial updates like status changes.
     *
     * Why PATCH instead of PUT for status change?
     *   PUT = replace the entire resource (all fields required)
     *   PATCH = partial update (only send what you want to change)
     *
     *   For status change we only send { "status": "IN_PROGRESS" }
     *   PUT would require sending title, description, assignedTo too.
     *   PATCH is the semantically correct HTTP verb here.
     *
     * Backend endpoint: PATCH /api/tasks/{id}/status
     *
     * @param path            e.g. "/tasks/5/status"
     * @param body            e.g. UpdateTaskRequest with only status set
     * @param typeReference   expected response type
     * @param authenticated   whether to attach Bearer token
     */
    public <T> ApiResponse<T> patch(String path, Object body,
                                    TypeReference<ApiResponse<T>> typeReference,
                                    boolean authenticated) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPatch patch = new HttpPatch(BASE_URL + path);
            patch.setHeader("Content-Type", "application/json");
            attachToken(patch, authenticated);
            if (body != null) {
                patch.setEntity(new StringEntity(
                        mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
            }
            try (CloseableHttpResponse response = httpClient.execute(patch)) {
                return parseResponse(response, typeReference);
            } catch (java.net.ConnectException e) {
                return connectionError();
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void attachToken(org.apache.hc.core5.http.HttpRequest request,
                             boolean authenticated) {
        if (authenticated) {
            CliConfig config = CliConfig.load();
            if (config != null && config.getAccessToken() != null) {
                request.setHeader("Authorization", "Bearer " + config.getAccessToken());
            }
        }
    }

    private <T> ApiResponse<T> connectionError() {
        return ApiResponse.<T>error(
                "Cannot connect to Sprintly backend at " + BASE_URL
                        + ". Is the server running?");
    }

    /**
     * Reads response body as String first then parses JSON.
     * Fixes MismatchedInputException when body is empty (401/403/204).
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

        if (body == null || body.isBlank()) {
            if (statusCode == 401) return ApiResponse.<T>error("Session expired. Run: sprintly login");
            if (statusCode == 403) return ApiResponse.<T>error("Access denied.");
            return ApiResponse.<T>error("Server returned empty response (HTTP " + statusCode + ").");
        }

        if (body.trim().startsWith("<")) {
            return ApiResponse.<T>error("Not authenticated. Run: sprintly login");
        }

        try {
            return mapper.readValue(body, typeReference);
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            return ApiResponse.<T>error(
                    "Unexpected server response (HTTP " + statusCode + "): "
                            + body.substring(0, Math.min(body.length(), 120)));
        }
    }
}