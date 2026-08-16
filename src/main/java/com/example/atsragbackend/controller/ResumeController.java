package com.example.atsragbackend.controller;

import com.example.atsragbackend.dto.ApifyScraperRequest;
import com.example.atsragbackend.dto.JobSearchQuery;
import com.example.atsragbackend.service.ResumeAnalyzerService;
import com.example.atsragbackend.service.ResumeParsingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "http://localhost:5173")
public class ResumeController {

    private final ResumeParsingService parsingService;
    private final ResumeAnalyzerService analyzerService;

    public ResumeController(ResumeParsingService parsingService, ResumeAnalyzerService analyzerService) {
        this.parsingService = parsingService;
        this.analyzerService = analyzerService;
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
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is missing or empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only PDF files are supported"));
        }

        try {
            // 1. Extract raw text from the resume
            String extractedText = parsingService.extractText(file);

            // 2. Generate search titles using the LLM
            JobSearchQuery searchQuery = analyzerService.generateSearchQuery(extractedText);

            // 3. Assemble the complete scraper request payload
            ApifyScraperRequest scraperRequest = ApifyScraperRequest.of(
                    searchQuery,
                    location,
                    experienceLevel,
                    jobType,
                    workplaceType,
                    datePosted
            );

            // 4. Return the assembled payload for verification
            return ResponseEntity.ok(Map.of(
                    "status", "PROCESSING",
                    "message", "Resume parsed and scraper request payload assembled successfully",
                    "scraperPayload", scraperRequest
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to parse PDF: " + e.getMessage()));
        }
    }
}