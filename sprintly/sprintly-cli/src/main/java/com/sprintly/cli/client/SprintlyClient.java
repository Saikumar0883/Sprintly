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
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;

public class SprintlyClient {
    private static final String BASE_URL = "http://localhost:8080/api";
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public <T> ApiResponse<T> post(String path, Object body, TypeReference<ApiResponse<T>> typeReference, boolean authenticated) throws IOException {
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
                return mapper.readValue(response.getEntity().getContent(), typeReference);
            } catch (com.fasterxml.jackson.core.JsonParseException e) {
                return ApiResponse.<T>error("Server returned an invalid JSON response (likely an HTML redirect). Ensure you are logged in! Run 'login'.");
            } catch (java.net.ConnectException e) {
                return ApiResponse.<T>error("Connection Refused: Is the Sprintly Backend API running on " + BASE_URL + "?");
            }
        }
    }

    public <T> ApiResponse<T> get(String path, TypeReference<ApiResponse<T>> typeReference, boolean authenticated) throws IOException {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(BASE_URL + path);

            if (authenticated) {
                CliConfig config = CliConfig.load();
                if (config != null && config.getAccessToken() != null) {
                    get.setHeader("Authorization", "Bearer " + config.getAccessToken());
                }
            }

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                return mapper.readValue(response.getEntity().getContent(), typeReference);
            } catch (com.fasterxml.jackson.core.JsonParseException e) {
                return ApiResponse.<T>error("Server returned an HTML or invalid response. Please ensure you are logged in! Run 'login'.");
            } catch (java.net.ConnectException e) {
                return ApiResponse.<T>error("Connection Refused: Is the Sprintly Backend API running on " + BASE_URL + "?");
            }
        }
    }
}
