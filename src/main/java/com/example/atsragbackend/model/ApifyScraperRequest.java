package com.example.atsragbackend.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApifyScraperRequest(
        String searchQuery,
        List<String> searchQueries,
        String location,
        Integer maxJobs,
        String jobType,
        String experienceLevel,
        String workplaceType,
        String datePosted,
        Boolean scrapeJobDetails
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
                20,
                (jobType != null && !jobType.isBlank()) ? jobType : "all",
                (experienceLevel != null && !experienceLevel.isBlank()) ? experienceLevel : "all",
                (workplaceType != null && !workplaceType.isBlank()) ? workplaceType : "all",
                (datePosted != null && !datePosted.isBlank()) ? datePosted : "all",
                true
        );
    }
}