package com.example.atsragbackend.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExperienceFilterUtil {

    // Pattern 1: Explicit header like "Experience: 8+ Years" or "Exp: 3-5 yrs"
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "(?i)(?:experience|exp)\\s*[:\\-]\\s*(\\d{1,2})\\s*(?:\\+|\\-\\s*\\d{1,2})?\\s*(?:years?|yrs?)?"
    );

    // Pattern 2: Phrase like "8+ years of experience", "min 3 yrs", "2 to 4 years"
    private static final Pattern PHRASE_PATTERN = Pattern.compile(
            "(?i)(?:minimum|min|at\\s+least)?\\s*(\\d{1,2})\\s*(?:\\+|\\-\\s*\\d{1,2}|\\s+to\\s+\\d{1,2})?\\s*(?:years?|yrs?)\\s*(?:of)?\\s*(?:relevant|industry|work|total)?\\s*(?:experience|exp)?"
    );

    /**
     * Extracts the highest required minimum years of experience mentioned in the text.
     * Returns -1 if no experience requirements are detected.
     */
    public static int extractRequiredYears(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }

        List<Integer> detectedYears = new ArrayList<>();

        // Check header pattern
        Matcher headerMatcher = HEADER_PATTERN.matcher(text);
        while (headerMatcher.find()) {
            try {
                int years = Integer.parseInt(headerMatcher.group(1));
                if (years <= 30) { // Discard unrealistic numbers/outliers
                    detectedYears.add(years);
                }
            } catch (NumberFormatException ignored) {}
        }

        // Check phrase pattern
        Matcher phraseMatcher = PHRASE_PATTERN.matcher(text);
        while (phraseMatcher.find()) {
            try {
                int years = Integer.parseInt(phraseMatcher.group(1));
                if (years <= 30) {
                    detectedYears.add(years);
                }
            } catch (NumberFormatException ignored) {}
        }

        if (detectedYears.isEmpty()) {
            return -1; // No explicit YoE found
        }

        // Use the highest requirement found (e.g., if a job mentions 8+ yrs in IT and 2 yrs in React)
        return Collections.max(detectedYears);
    }

    /**
     * Checks if the job is within the candidate's acceptable YoE range (± 2 years).
     * If no experience is mentioned, it returns true (fail-open strategy).
     */
    public static boolean isWithinExperienceRange(String jdText, int targetYoe) {
        int requiredYoe = extractRequiredYears(jdText);

        // Fail-open: if the JD doesn't mention years, let it pass to vector search
        if (requiredYoe == -1) {
            return true;
        }

        int minAllowed = Math.max(0, targetYoe - 2);
        int maxAllowed = targetYoe + 2;

        return requiredYoe >= minAllowed && requiredYoe <= maxAllowed;
    }

    /**
     * Maps the Apify experienceLevel code to an approximate target YoE.
     */
    public static int mapExperienceLevelToYears(String experienceLevel) {
        return switch (experienceLevel) {
            case "1" -> 0; // Internship
            case "2" -> 1; // Entry level (0-2 years)
            case "3" -> 3; // Associate (2-4 years)
            case "4" -> 6; // Mid-Senior (4-8 years)
            case "5" -> 10; // Director
            case "6" -> 15; // Executive
            default -> 1;  // Default fallback to entry/junior
        };
    }
}