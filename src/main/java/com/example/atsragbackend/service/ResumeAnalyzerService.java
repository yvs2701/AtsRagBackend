package com.example.atsragbackend.service;

import com.example.atsragbackend.model.JobSearchQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
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
                "required": ["searchQuery", "searchQueries"],
                "additionalProperties": false
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
        log.info("Generating JobSearchQuery for resume text.");
        JobSearchQuery jobSearchQuery = this.chatClient.prompt()
                .system(systemPrompt)
                .user(userSpec -> userSpec
                        .text(userPromptTemplate)
                        .param("resume", resumeText)
                )
                .options(OpenAiChatOptions.builder()
                        .responseFormat(OpenAiChatModel.ResponseFormat.builder()
                                .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                                .jsonSchema(SEARCH_QUERY_JSON_SCHEMA)
                                .build()))
                .call()
                .entity(JobSearchQuery.class);

        log.info("Generated JobSearchQuery: {}", jobSearchQuery);
        return jobSearchQuery;
    }

    public String generateMatchReason(String resumeText, String jobDescription) {
        final String systemPrompt = """
                Compare the following resume to the job description.
                Resume text is all the text under "Resume text:" and above "====================" (line of equal signs). Job description follows after "Job description:" till the end of the input.
                Provide a 1-2 sentence reason explaining why this candidate is a good fit for this specific job.
                Do not mention the candidate's name or any personal identifiers, use "the candidate" instead of their name. Focus on skills, experience, and qualifications that align with the job requirements.
                Output ONLY the plain text reason. Do not include markdown code blocks, conversational text, or internal reasoning.
                """;
        final String userPromptTemplate = """
                Resume text:
                {resume}
                
                ====================
                
                Job description:
                {job}
                """;

        log.info("Generating match reason for resume and job description.");
        String matchReason = this.chatClient.prompt()
                .system(systemPrompt)
                .user(userSpec -> userSpec
                        .text(userPromptTemplate)
                        .param("resume", resumeText)
                        .param("job", jobDescription)
                )
                .call()
                .content();
        log.debug("Generated match reason: {}", matchReason);
        return matchReason;
    }
}