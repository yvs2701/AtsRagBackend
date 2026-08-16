package com.example.atsragbackend.model;

public record JdMatchResult(
        String title,
        String company,
        String url,
        long matchScore,
        String reason
) {}