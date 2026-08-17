package com.example.atsragbackend.service;

import com.example.atsragbackend.model.JobSearchQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

@Service
public class ResumeAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalyzerService.class);

    private final ChatClient chatClient;

    private static final String SEARCH_QUERY_JSON_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "searchQuery": { "type": "string" },
                    "searchQueries": {
                        "type": "array",
                        "items": { "type": "string" }
                    }
                },
                "required": ["searchQuery", "searchQueries"]
            }
            """;

    public ResumeAnalyzerService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public JobSearchQuery generateSearchQuery(String resumeText) {
        final String systemPrompt = """
                Analyze the following resume text and extract job search titles.
                
                Provide:
                1. "searchQuery": The primary job title matching the candidate's core profile and skill set.
                2. "searchQueries": Up to 3 closely related skills and optionally up to 2 alternative job titles to expand search coverage.
                """;

        final String userPromptTemplate = """
                Resume Text:
                {resume}
                """;

        JobSearchQuery jobSearchQuery = this.chatClient.prompt()
                .system(systemPrompt)
                .user(userSpec -> userSpec
                        .text(userPromptTemplate)
                        .param("resume", resumeText)
                )
                // Inject the strict JSON schema only for this specific API call
                .options(OllamaChatOptions.builder()
                        .format("json")
                        .outputSchema(SEARCH_QUERY_JSON_SCHEMA))
                .call()
                .entity(JobSearchQuery.class);

        log.info("Generated JobSearchQuery: {}", jobSearchQuery);
        return jobSearchQuery;
    }

    public String generateMatchReason(String resumeText, String jobDescription) {
        final String systemPrompt = """
                Compare the following resume to the job description.
                Provide a 1-2 sentence reason explaining why this candidate is a good fit for this specific job.
                Output ONLY the text reason. Do not include markdown code blocks, conversational text, or internal reasoning.
                """;
        final String userPromptTemplate = """
                Resume:
                {resume}
                
                Job Description:
                {job}
                """;

        // Omitting .options() defaults back to plain text generation
        String matchReason = this.chatClient.prompt()
                .system(systemPrompt)
                .user(userSpec -> userSpec
                        .text(userPromptTemplate)
                        .param("resume", resumeText)
                        .param("job", jobDescription)
                )
                .call()
                .content();
        log.info("Generated match reason: {}", matchReason);
        return matchReason;
    }
}