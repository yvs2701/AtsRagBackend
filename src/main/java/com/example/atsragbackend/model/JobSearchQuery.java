package com.example.atsragbackend.model;

import java.util.List;

public record JobSearchQuery(
        String searchQuery,         // Single search query (skill, job title, company name, etc.)
        List<String> searchQueries  // Alternative search queries
) {
}