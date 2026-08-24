package com.example.atsragbackend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.multipart.MultipartFile;

public record ResumeUploadRequest(
        @NotNull(message = "Resume file is missing!")
        @Schema(type = "string", format = "binary", description = "The resume PDF file")
        MultipartFile file,

        @Schema(description = "Job location")
        String location,

        @Pattern(regexp = "^(all|1|2|3|4|5|6)$", message = "experienceLevel must be 1, 2, 3, 4, 5, 6, or 'all' (default)")
        @Schema(description = "1=Internship, 2=Entry, 3=Associate, 4=Mid-Senior, 5=Director, 6=Executive",
                defaultValue = "all",
                allowableValues = {"all", "1", "2", "3", "4", "5", "6"})
        String experienceLevel,

        @Pattern(regexp = "^(all|F|P|C|T|I)$", message = "jobType must be F, P, C, T, I, or 'all' (default)")
        @Schema(description = "F=Full-time, P=Part-time, C=Contract, T=Temporary, I=Internship",
                defaultValue = "all",
                allowableValues = {"all", "F", "P", "C", "T", "I"})
        String jobType,

        @Pattern(regexp = "^(all|1|2|3)$", message = "workplaceType must be 1, 2, 3, or 'all' (default)")
        @Schema(description = "1=On-site, 2=Remote, 3=Hybrid",
                defaultValue = "all",
                allowableValues = {"all", "1", "2", "3"})
        String workplaceType,

        @Pattern(regexp = "^(all|r86400|r604800|r2592000)$", message = "datePosted must be 'r86400' (Past 24h), 'r604800' (Past week), 'r2592000' (Past month, default), or 'all'")
        @Schema(description = "r86400=Past 24h, r604800=Past week, r2592000=Past month",
                defaultValue = "r2592000",
                allowableValues = {"all", "r86400", "r604800", "r2592000"})
        String datePosted
) {
    public ResumeUploadRequest {
        location = location != null ? location : "";
        experienceLevel = experienceLevel != null ? experienceLevel : "all";
        jobType = jobType != null ? jobType : "all";
        workplaceType = workplaceType != null ? workplaceType : "all";
        datePosted = datePosted != null ? datePosted : "r2592000";
    }
}