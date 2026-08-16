package com.example.atsragbackend.service;

import com.example.atsragbackend.entity.MatchTask;
import com.example.atsragbackend.model.ApifyJob;
import com.example.atsragbackend.model.ApifyScraperRequest;
import com.example.atsragbackend.model.JobSearchQuery;
import com.example.atsragbackend.repository.MatchTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JobMatchService {

    private static final Logger log = LoggerFactory.getLogger(JobMatchService.class);

    private final MatchTaskRepository repository;
    private final ResumeAnalyzerService analyzerService;
    private final ApifyService apifyService;
    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore;

    public JobMatchService(MatchTaskRepository repository,
                           ResumeAnalyzerService analyzerService,
                           ApifyService apifyService,
                           ObjectMapper objectMapper,
                           VectorStore vectorStore) {
        this.repository = repository;
        this.analyzerService = analyzerService;
        this.apifyService = apifyService;
        this.objectMapper = objectMapper;
        this.vectorStore = vectorStore;
    }

    @Async // Removed the explicit task executor name to use virtual threads
    public void processResumeTask(String taskId, String extractedText, String location, String experienceLevel, String jobType, String workplaceType, String datePosted) {
        try {
            JobSearchQuery searchQuery = analyzerService.generateSearchQuery(extractedText);

            ApifyScraperRequest scraperRequest = ApifyScraperRequest.of(
                    searchQuery, location, experienceLevel, jobType, workplaceType, datePosted
            );

            String scrapedJobsJson = apifyService.scrapeJobs(scraperRequest);
            log.debug("scrapedJobsJson: {}", scrapedJobsJson);
            List<ApifyJob> scrapedJobs = objectMapper.readValue(scrapedJobsJson, new TypeReference<>() {
            });

            if (scrapedJobs.isEmpty()) {
                updateTaskStatus(taskId, MatchTask.TaskStatus.SUCCESS, "[]");
                return;
            }

            List<Document> documents = scrapedJobs.stream()
                    .map(job -> new Document(
                            UUID.randomUUID().toString(),
                            job.description() != null ? job.description() : "",
                            Map.of(
                                    "taskId", taskId,
                                    "title", job.title() != null ? job.title() : "Unknown",
                                    "company", job.companyName() != null ? job.companyName() : "Unknown", // Updated here
                                    "url", job.url() != null ? job.url() : ""
                            )
                    ))
                    .toList();

            vectorStore.add(documents);

            SearchRequest searchRequest = SearchRequest.builder()
                    .query(extractedText)
                    .topK(5)
                    .filterExpression("taskId == '" + taskId + "'")
                    .build();

            List<Document> topMatches = vectorStore.similaritySearch(searchRequest);

            List<Map<String, Object>> bestJobs = topMatches.stream() // Use .parallelStream() if your Ollama instance is configured for concurrent requests
                    .map(doc -> {
                        Object distanceObj = doc.getMetadata().get("distance");
                        double distance = distanceObj instanceof Number ? ((Number) distanceObj).doubleValue() : 0.0;
                        long matchScore = Math.round((1.0 - distance) * 100.0);

                        // Generate the AI reason by comparing the resume to the scraped job description
                        String reason = analyzerService.generateMatchReason(extractedText,
                                doc.getText());

                        return Map.of(
                                "title", doc.getMetadata().get("title"),
                                "company", doc.getMetadata().get("company"),
                                "url", doc.getMetadata().get("url"),
                                "matchScore", matchScore,
                                "reason", reason // Added reason field
                        );
                    })
                    .toList();

            String finalResultJson = objectMapper.writeValueAsString(bestJobs);

            updateTaskStatus(taskId, MatchTask.TaskStatus.SUCCESS, finalResultJson);

            List<String> documentIds = documents.stream().map(Document::getId).toList();
            vectorStore.delete(documentIds);

        } catch (Exception e) {
            updateTaskStatus(taskId, MatchTask.TaskStatus.FAILED, e.getMessage());
        }
    }

    private void updateTaskStatus(String taskId, MatchTask.TaskStatus status, String payload) {
        repository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            task.setResultPayload(payload);
            repository.save(task);
        });
    }
}