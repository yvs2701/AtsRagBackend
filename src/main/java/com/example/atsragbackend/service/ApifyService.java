package com.example.atsragbackend.service;

import com.example.atsragbackend.exception.ApifyJobStillRunningException;
import com.example.atsragbackend.model.ApifyScraperRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Service
public class ApifyService {

    private static final Logger log = LoggerFactory.getLogger(ApifyService.class);
    private final WebClient webClient;
    private final String apiToken;
    private final ObjectMapper objectMapper;
    private static final String ACTOR_ID = "automation-lab~linkedin-jobs-scraper";
    private static final int RETRY_DELAY_SECONDS = 15; // retry every 15 seconds
    private static final int MAX_RETRIES = 40; // Retry 60 times (40 * 15s = 600s = 10 minutes)

    public ApifyService(WebClient.Builder webClientBuilder,
                        @Value("${apify.api.token}") String apiToken,
                        ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("https://api.apify.com/v2").build();
        this.apiToken = apiToken;
        this.objectMapper = objectMapper;
    }

    public String scrapeJobs(ApifyScraperRequest request) {
        try {
            String runResponseStr = webClient.post()
                    .uri("/acts/{actorId}/runs?token={token}", ACTOR_ID, apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // Called by @Async code. Safe to block the thread here.

            JsonNode runResponse = objectMapper.readTree(runResponseStr);

            if (runResponse == null || !runResponse.hasNonNull("data")) {
                throw new RuntimeException("Invalid response from Apify upon triggering the actor.");
            }

            String runId = runResponse.path("data").path("id").asText();
            log.info("Started Apify run with ID: {}", runId);

            // Create a reactive pipeline that fetches status and retries every 10s if not finished
            return Mono.defer(() -> webClient.get()
                            .uri("/actor-runs/{runId}?token={token}", runId, apiToken)
                            .retrieve()
                            .bodyToMono(String.class))
                    .flatMap(statusResponseStr -> {
                        try {
                            JsonNode statusResponse = objectMapper.readTree(statusResponseStr);

                            if (statusResponse == null || !statusResponse.hasNonNull("data")) {
                                return Mono.error(new RuntimeException("Received null or malformed status response from Apify."));
                            }

                            String status = statusResponse.path("data").path("status").asText();
                            log.info("Apify run {} status: {}", runId, status);

                            if ("SUCCEEDED".equals(status)) {
                                String datasetId = statusResponse.path("data").path("defaultDatasetId").asText();
                                log.info("Apify run {} succeeded. Fetching results from dataset ID: {}", runId, datasetId);

                                return webClient.mutate()
                                        // set max in-memory size to 512KB
                                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(512 * 1024))
                                        .build()
                                        .get()
                                        .uri("/datasets/{datasetId}/items?token={token}", datasetId, apiToken)
                                        .retrieve()
                                        .bodyToMono(String.class)
                                        .doOnSuccess(res -> log.info("Successfully fetched dataset items for Apify run {} (Dataset ID: {})", runId, datasetId))
                                        .doOnError(e -> log.error("Failed to fetch scraped dataset for Apify run {} (Dataset ID: {}). Error: {}", runId, datasetId, e.getMessage(), e));

                            } else if ("FAILED".equals(status) || "ABORTED".equals(status)) {
                                return Mono.error(new RuntimeException("Apify scraper failed with status: " + status));
                            } else {
                                // Status is RUNNING or READY. Throw custom exception to trigger the retry cycle.
                                return Mono.error(new ApifyJobStillRunningException("Status is: " + status));
                            }
                        } catch (Exception e) {
                            return Mono.error(e);
                        }
                    })
                    // Only retries if the error is our custom ApifyJobStillRunningException.
                    .retryWhen(Retry.fixedDelay(MAX_RETRIES, Duration.ofSeconds(RETRY_DELAY_SECONDS))
                            .filter(throwable ->
                                    throwable instanceof ApifyJobStillRunningException))
                    .block();

        } catch (Exception e) {
            throw new RuntimeException("Apify execution failed: " + e.getMessage(), e);
        }
    }
}