package com.example.atsragbackend.dto;

import java.util.List;

public record JobSearchQuery(
        String searchQuery,         // Primary target job title (e.g., "Full Stack Java Developer")
        List<String> searchQueries  // 2-3 Alternative titles (e.g., ["Java Developer", "Backend Engineer"])
) {}