package com.example.atsragbackend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApifyJob(
        String title,
        @JsonProperty("companyName") String companyName,
        String location,
        String url,
        String description
) {
}