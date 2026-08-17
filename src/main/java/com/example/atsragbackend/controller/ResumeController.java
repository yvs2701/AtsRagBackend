package com.example.atsragbackend.controller;

import com.example.atsragbackend.entity.MatchTask;
import com.example.atsragbackend.model.JdMatchResult;
import com.example.atsragbackend.repository.MatchTaskRepository;
import com.example.atsragbackend.service.JobMatchService;
import com.example.atsragbackend.service.ResumeParsingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "http://localhost:5173")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    private final ResumeParsingService parsingService;
    private final JobMatchService jobMatchService;
    private final MatchTaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    public ResumeController(ResumeParsingService parsingService,
                            JobMatchService jobMatchService,
                            MatchTaskRepository taskRepository,
                            ObjectMapper objectMapper) {
        this.parsingService = parsingService;
        this.jobMatchService = jobMatchService;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "location", defaultValue = "") String location,
            @RequestParam(value = "experienceLevel", defaultValue = "all") String experienceLevel,
            @RequestParam(value = "jobType", defaultValue = "all") String jobType,
            @RequestParam(value = "workplaceType", defaultValue = "all") String workplaceType,
            @RequestParam(value = "datePosted", defaultValue = "all") String datePosted
    ) {
        log.info("Received resume upload request. Filename: {}, Size: {} bytes. Location: {}, Experience: {}, JobType: {}",
                file != null ? file.getOriginalFilename() : "null",
                file != null ? file.getSize() : 0,
                location, experienceLevel, jobType);

        if (file == null || file.isEmpty()) {
            log.warn("Upload rejected: File is missing or empty.");
            return ResponseEntity.badRequest().body(Map.of("error", "File is missing"));
        }

        if (!"application/pdf".equalsIgnoreCase(file.getContentType())) {
            log.warn("Upload rejected: Invalid content type ({}). Only PDFs are supported.", file.getContentType());
            return ResponseEntity.badRequest().body(Map.of("error", "Only PDFs supported"));
        }

        try {
            String extractedText = parsingService.extractText(file);
            String taskId = UUID.randomUUID().toString();

            log.info("Successfully extracted text. Created MatchTask with ID: {}", taskId);
            MatchTask task = new MatchTask(taskId, MatchTask.TaskStatus.PROCESSING, extractedText);
            taskRepository.save(task);

            log.debug("Dispatching async job matching process for task ID: {}", taskId);
            jobMatchService.processResumeTask(
                    taskId, extractedText, location, experienceLevel, jobType, workplaceType, datePosted
            );

            return ResponseEntity.accepted().body(Map.of("taskId", taskId, "status", "PROCESSING"));

        } catch (Exception e) {
            log.error("Failed to process resume upload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{taskId}")
    public ResponseEntity<?> getStatus(@PathVariable String taskId) {
        log.debug("Status check requested for task ID: {}", taskId);

        Optional<MatchTask> taskOptional = taskRepository.findById(taskId);

        if (taskOptional.isEmpty()) {
            log.warn("Status check failed: Task ID {} not found or already deleted.", taskId);
            return ResponseEntity.status(404).body(Map.of("error", "Task not found or already completed/deleted"));
        }

        MatchTask task = taskOptional.get();

        if (task.getStatus() == MatchTask.TaskStatus.PROCESSING) {
            log.debug("Task ID {} is still PROCESSING.", taskId);
            return ResponseEntity.ok(Map.of("taskId", taskId, "status", "PROCESSING"));
        }

        log.info("Task ID {} reached terminal status: {}", taskId, task.getStatus());

        String responsePayload = task.getResultPayload();
        String responseStatus = task.getStatus().name();

        List<JdMatchResult> parsedResult = null;
        try {
            if (task.getStatus() == MatchTask.TaskStatus.SUCCESS && responsePayload != null && !responsePayload.isBlank()) {
                log.debug("Parsing JSON result payload for task ID: {}", taskId);
                parsedResult = objectMapper.readValue(responsePayload, new TypeReference<>() {
                });
            }
        } catch (Exception e) {
            log.error("Failed to parse result payload for task ID {}: {}", taskId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to parse result payload: " + e.getMessage()));
        }

        log.debug("Deleting completed task ID {} from database.", taskId);
        taskRepository.deleteById(taskId);

        parsedResult = parsedResult != null ? parsedResult : List.of();

        return ResponseEntity.ok(Map.of(
                "taskId", taskId,
                "status", responseStatus,
                "result", parsedResult
        ));
    }
}