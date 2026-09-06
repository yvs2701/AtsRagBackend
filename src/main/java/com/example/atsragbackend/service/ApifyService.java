package com.example.atsragbackend.service;

import com.example.atsragbackend.exception.ApifyJobStillRunningException;
import com.example.atsragbackend.model.ApifyJob;
import com.example.atsragbackend.model.ApifyRunResponse;
import com.example.atsragbackend.model.ApifyScraperRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Service
public class ApifyService {

    private static final Logger log = LoggerFactory.getLogger(ApifyService.class);
    private final WebClient webClient;
    private final String apiToken;
    private static final String ACTOR_ID = "automation-lab~linkedin-jobs-scraper";
    private static final int RETRY_DELAY_SECONDS = 15; // retry every 15 seconds
    private static final int MAX_RETRIES = 40; // Retry 60 times (40 * 15s = 600s = 10 minutes)

    // Extract field names to fetch from the dataset in Apify storage
    private static final String requiredFields = String.join(",", Arrays.asList(
            "title", "location", "companyName", "url", "descriptionText",
            "seniorityLevel", "employmentType"));

    public ApifyService(WebClient.Builder webClientBuilder,
                        @Value("${apify.api.token}") String apiToken) {
        this.webClient = webClientBuilder.baseUrl("https://api.apify.com/v2").build();
        this.apiToken = apiToken;
    }

    public List<ApifyJob> scrapeJobs(ApifyScraperRequest request) {
        try {
            ApifyRunResponse runResponse = webClient.post()
                    .uri("/acts/{actorId}/runs?token={token}", ACTOR_ID, apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ApifyRunResponse.class)
                    .block(); // Called by @Async code. Safe to block the thread here.

            if (runResponse == null || runResponse.data() == null || runResponse.data().id() == null) {
                throw new RuntimeException("Invalid response from Apify upon triggering the actor.");
            }

            String runId = runResponse.data().id();
            log.info("Started Apify run with ID: {}", runId);

            // Create a reactive pipeline that fetches status and retries every 10s if not finished
            return Mono.defer(() -> webClient.get()
                            .uri("/actor-runs/{runId}?token={token}", runId, apiToken)
                            .retrieve()
                            .bodyToMono(ApifyRunResponse.class))
                    .flatMap(statusResponse -> {
                        try {
                            if (statusResponse.data() == null) {
                                return Mono.error(new RuntimeException("Received null or malformed status response from Apify."));
                            }

                            String status = statusResponse.data().status();
                            log.info("Apify run {} status: {}", runId, status);

                            if ("SUCCEEDED".equals(status)) {
                                String datasetId = statusResponse.data().defaultDatasetId();
                                log.info("Apify run {} succeeded. Fetching results from dataset ID: {}", runId, datasetId);

                                return webClient
                                        .get()
                                        .uri(uriBuilder -> uriBuilder
                                                .path("/datasets/{datasetId}/items")
                                                .queryParam("token", apiToken)
                                                // Only fetch the fields required to deserialize into ApifyJob
                                                .queryParam("fields", requiredFields)
                                                .build(datasetId))
                                        .retrieve()
                                        .bodyToFlux(ApifyJob.class)
                                        .collectList()
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