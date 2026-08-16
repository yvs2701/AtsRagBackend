package com.example.atsragbackend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApifyJob(
        String title,
        String location,
        String companyName,
        String url,
        String descriptionText,
        String seniorityLevel,
        String employmentType
) {
}