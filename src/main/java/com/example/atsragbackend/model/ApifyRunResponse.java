package com.example.atsragbackend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApifyRunResponse(
        ApifyRunData data
) {
}