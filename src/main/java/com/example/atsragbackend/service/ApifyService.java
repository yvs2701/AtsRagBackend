package com.example.atsragbackend.service;

import com.example.atsragbackend.model.ApifyScraperRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ApifyService {

    private static final Logger log = LoggerFactory.getLogger(ApifyService.class);
    private final RestClient restClient;
    private final String apiToken;
    private final ObjectMapper objectMapper;
    private static final String ACTOR_ID = "automation-lab~linkedin-jobs-scraper";

    public ApifyService(RestClient.Builder restClientBuilder,
                        @Value("${apify.api.token}") String apiToken,
                        ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl("https://api.apify.com/v2").build();
        this.apiToken = apiToken;
        this.objectMapper = objectMapper;
    }

    public String scrapeJobs(ApifyScraperRequest request) {
        try {
            // Fetch as String, then parse to JsonNode to avoid abstract instantiation errors
            String runResponseStr = restClient.post()
                    .uri("/acts/{actorId}/runs?token={token}", ACTOR_ID, apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            JsonNode runResponse = objectMapper.readTree(runResponseStr);

            if (runResponse == null || !runResponse.hasNonNull("data")) {
                throw new RuntimeException("Invalid response from Apify upon triggering the actor.");
            }

            String runId = runResponse.path("data").path("id").asText();
            log.info("Started Apify run with ID: {}", runId);

            while (true) {
                Thread.sleep(10000);

                // Fetch as String, then parse to JsonNode
                String statusResponseStr = restClient.get()
                        .uri("/actor-runs/{runId}?token={token}", runId, apiToken)
                        .retrieve()
                        .body(String.class);

                JsonNode statusResponse = objectMapper.readTree(statusResponseStr);

                if (statusResponse == null || !statusResponse.hasNonNull("data")) {
                    throw new RuntimeException("Received null or malformed status response from Apify.");
                }

                String status = statusResponse.path("data").path("status").asText();
                log.info("Apify run {} status: {}", runId, status);

                if ("SUCCEEDED".equals(status)) {
                    String datasetId = statusResponse.path("data")
                            .path("defaultDatasetId").asText();
                    log.info("Apify run {} succeeded. Fetching results from dataset ID: {}", runId, datasetId);
                    try {
                        String datasetResult = restClient.get()
                                .uri("/datasets/{datasetId}/items?token={token}",
                                        datasetId, apiToken)
                                .retrieve()
                                .body(String.class);
                        log.info("Successfully fetched dataset items for run {} (Dataset ID: {})", runId, datasetId);
                        return datasetResult;

                    } catch (Exception e) {
                        log.error("Failed to fetch scraped dataset for Apify run {} (Dataset ID: {}). Error: {}", runId, datasetId, e.getMessage(), e);
                        throw new RuntimeException("Error fetching Apify dataset: " + e.getMessage(), e);
                    }
                } else if ("FAILED".equals(status) || "ABORTED".equals(status)) {
                    throw new RuntimeException("Apify scraper failed with status: " + status);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Apify execution failed: " + e.getMessage(), e);
        }
    }
}