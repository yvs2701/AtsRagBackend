package com.example.atsragbackend.service;

import com.example.atsragbackend.entity.MatchTask;
import com.example.atsragbackend.model.ApifyJob;
import com.example.atsragbackend.model.ApifyScraperRequest;
import com.example.atsragbackend.model.JdMatchResult;
import com.example.atsragbackend.model.JobSearchQuery;
import com.example.atsragbackend.repository.MatchTaskRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        try {
            JobSearchQuery searchQuery = analyzerService.generateSearchQuery(extractedText);

            ApifyScraperRequest scraperRequest = ApifyScraperRequest.of(
                    searchQuery, location, experienceLevel, jobType, workplaceType, datePosted
            );

            String scrapedJobsJson = apifyService.scrapeJobs(scraperRequest);
            List<ApifyJob> scrapedJobs = objectMapper.readValue(scrapedJobsJson, new TypeReference<>() {
            });

            if (scrapedJobs.isEmpty()) {
                updateTaskStatus(taskId, MatchTask.TaskStatus.SUCCESS, "[]");
                return;
            }

            List<Document> documents = scrapedJobs.stream()
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

            vectorStore.add(documents);

            SearchRequest searchRequest = SearchRequest.builder()
                    .query(extractedText)
                    .topK(5)
                    .filterExpression(String.format("taskId == '%s'", taskId))
                    .build();

            List<Document> topMatches = vectorStore.similaritySearch(searchRequest);

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