package com.example.atsragbackend.dto;

import java.util.List;

public record ApifyScraperRequest(
        String searchQuery,
        List<String> searchQueries,
        String location,
        int maxJobs,
        String jobType,
        String experienceLevel,
        String workplaceType,
        String datePosted,
        boolean scrapeJobDetails
) {
    public static ApifyScraperRequest of(
            JobSearchQuery query,
            String location,
            String experienceLevel,
            String jobType,
            String workplaceType,
            String datePosted
    ) {
        return new ApifyScraperRequest(
                query.searchQuery(),
                query.searchQueries(),
                (location != null && !location.isBlank()) ? location : "",
                20, // Updated to documentation default
                (jobType != null && !jobType.isBlank()) ? jobType : "all",
                (experienceLevel != null && !experienceLevel.isBlank()) ? experienceLevel : "all",
                (workplaceType != null && !workplaceType.isBlank()) ? workplaceType : "all",
                (datePosted != null && !datePosted.isBlank()) ? datePosted : "all",
                true
        );
    }
}