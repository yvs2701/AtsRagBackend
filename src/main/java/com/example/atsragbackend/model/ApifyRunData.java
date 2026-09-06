package com.example.atsragbackend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApifyRunData(
        String id,
        String status,
        String defaultDatasetId
) {
}