package com.example.atsragbackend.service;

import com.example.atsragbackend.entity.MatchTask;
import com.example.atsragbackend.model.ApifyJob;
import com.example.atsragbackend.model.ApifyScraperRequest;
import com.example.atsragbackend.model.JdMatchResult;
import com.example.atsragbackend.model.JobSearchQuery;
import com.example.atsragbackend.repository.MatchTaskRepository;
import com.example.atsragbackend.util.ExperienceFilterUtil;
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

    @Async
    public void processResumeTask(String taskId, String extractedText, String location, String experienceLevel, String jobType, String workplaceType, String datePosted) {
        log.info("Starting processing for taskId: {}. Location: {}, Experience: {}, JobType: {}, WorkplaceType: {}, DatePosted: {}", taskId, location, experienceLevel, jobType, workplaceType, datePosted);

        try {
            log.info("Generating search queries from resume text for taskId: {}", taskId);
            JobSearchQuery searchQuery = analyzerService.generateSearchQuery(extractedText);

            ApifyScraperRequest scraperRequest = ApifyScraperRequest.of(
                    searchQuery, location, experienceLevel, jobType, workplaceType, datePosted
            );

            log.info("Triggering Apify scraper for taskId: {}", taskId);
            String scrapedJobsJson = apifyService.scrapeJobs(scraperRequest);
            List<ApifyJob> scrapedJobs = objectMapper.readValue(scrapedJobsJson,
                    new TypeReference<>() {
                    });
            log.info("Scraped {} jobs from Apify for taskId: {}", scrapedJobs.size(), taskId);

            if (scrapedJobs.isEmpty()) {
                log.warn("No jobs returned from Apify scraper for taskId: {}. Completing task with empty results.", taskId);
                updateTaskStatus(taskId, MatchTask.TaskStatus.SUCCESS, "[]");
                return;
            }

            // --- PRE-FILTERING STEP ---
            int targetYoe = ExperienceFilterUtil.mapExperienceLevelToYears(experienceLevel);

            List<ApifyJob> filteredJobs = scrapedJobs.stream()
                    .filter(job -> {
                        String description = job.descriptionText();
                        boolean matches = ExperienceFilterUtil.isWithinExperienceRange(description, targetYoe);

                        if (!matches) {
                            int req = ExperienceFilterUtil.extractRequiredYears(description);
                            log.info("Filtering out job '{}' at '{}' - Required YoE: {}, Target YoE: {}",
                                    job.title(), job.companyName(), req, targetYoe);
                        }
                        return matches;
                    })
                    .toList();

            log.info("Jobs remaining after YoE pre-filtering: {} / {}", filteredJobs.size(), scrapedJobs.size());

            if (filteredJobs.isEmpty()) {
                log.warn("All jobs were filtered out due to YoE mismatch for taskId: {}", taskId);
                updateTaskStatus(taskId, MatchTask.TaskStatus.SUCCESS, "[]");
                return;
            }

            // Convert only the filtered jobs to VectorStore documents
            log.debug("Mapping {} filtered jobs to VectorStore documents for taskId: {}", filteredJobs.size(), taskId);
            List<Document> documents = filteredJobs.stream()
                    .map(job -> {
                        final String jobDescription = String.format(
                                "Job Title: %s\nSeniority: %s\nEmployment Type: %s\nDescription: %s",
                                job.title() != null ? job.title() : "Unknown",
                                job.seniorityLevel() != null ? job.seniorityLevel() : "Unknown",
                                job.employmentType() != null ? job.employmentType() : "Unknown",
                                job.descriptionText() != null ? job.descriptionText() : ""
                        );

                        return new Document(
                                UUID.randomUUID().toString(),
                                jobDescription,
                                Map.of(
                                        "taskId", taskId,
                                        "title", job.title() != null ? job.title() : "Unknown",
                                        "company", job.companyName() != null ? job.companyName() : "Unknown",
                                        "location", job.location() != null ? job.location() : "Unknown",
                                        "url", job.url() != null ? job.url() : "",
                                        "seniorityLevel", job.seniorityLevel() != null ? job.seniorityLevel() : "Unknown",
                                        "employmentType", job.employmentType() != null ? job.employmentType() : "Unknown"
                                )
                        );
                    })
                    .toList();

            log.debug("Adding documents to VectorStore for taskId: {}", taskId);
            vectorStore.add(documents);

            log.info("Executing similarity search for taskId: {}", taskId);
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(extractedText)
                    .topK(5)
                    .filterExpression(String.format("taskId == '%s'", taskId))
                    .build();

            List<Document> topMatches = vectorStore.similaritySearch(searchRequest);
            log.info("Found {} top matches for taskId: {}", topMatches.size(), taskId);

            log.info("Generating AI match reasons and calculating scores for taskId: {}", taskId);
            List<JdMatchResult> bestJobs = topMatches.stream()
                    .map(doc -> {
                        Object distanceObj = doc.getMetadata().get("distance");
                        double distance = distanceObj instanceof Number ? ((Number) distanceObj).doubleValue() : 0.0;
                        long matchScore = Math.round((1.0 - distance) * 100.0);

                        final String jdTextContent = doc.getText();
                        String reason = analyzerService.generateMatchReason(extractedText, jdTextContent);

                        return new JdMatchResult(
                                (String) doc.getMetadata().get("title"),
                                (String) doc.getMetadata().get("company"),
                                (String) doc.getMetadata().get("url"),
                                matchScore,
                                reason
                        );
                    })
                    .toList();

            String finalResultJson = objectMapper.writeValueAsString(bestJobs);

            updateTaskStatus(taskId, MatchTask.TaskStatus.SUCCESS, finalResultJson);

            log.info("Deleting {} temporary vector documents from VectorStore for taskId: {}", documents.size(), taskId);
            List<String> documentIds = documents.stream().map(Document::getId).toList();
            vectorStore.delete(documentIds);

            log.info("Successfully processed resume task for taskId: {}", taskId);

        } catch (Exception e) {
            log.error("Error processing resume task {}: {}", taskId, e.getMessage(), e);
            updateTaskStatus(taskId, MatchTask.TaskStatus.FAILED, e.getMessage());
        }
    }

    private void updateTaskStatus(String taskId, MatchTask.TaskStatus status, String payload) {
        repository.findById(taskId).ifPresent(task -> {
            task.setStatus(status);
            task.setResultPayload(payload);
            repository.save(task);
            log.info("Task {} updated to status: {}, and payload: {}", taskId, status, payload);
        });
    }
}