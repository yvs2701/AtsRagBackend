package com.example.atsragbackend.service;

import com.example.atsragbackend.model.JobSearchQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ResumeAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalyzerService.class);

    private final ChatClient chatClient;

    public ResumeAnalyzerService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public JobSearchQuery generateSearchQuery(String resumeText) {
        String promptTemplate = """
                Analyze the following resume text and extract job search titles.
                
                Provide:
                1. "searchQuery": The primary job title matching the candidate's core profile and skill set.
                2. "searchQueries": Up to 3 closely related skills and optionally up to 2 alternative job titles to expand search coverage.
                
                Resume Text:
                {resume}
                """;

        JobSearchQuery jobSearchQuery = this.chatClient.prompt()
                .user(userSpec -> userSpec
                        .text(promptTemplate)
                        .param("resume", resumeText)
                )
                .call()
                .entity(JobSearchQuery.class);
        log.debug("Generated JobSearchQuery: {}", jobSearchQuery);
        return jobSearchQuery;
    }

    public String generateMatchReason(String resumeText, String jobDescription) {
        String promptTemplate = """
                Compare the following resume to the job description.
                Provide a 1-2 sentence reason explaining why this candidate is a good fit for this specific job.
                Output ONLY the text reason. Do not include markdown code blocks, conversational text, or internal reasoning.
                
                Resume:
                {resume}
                
                Job Description:
                {job}
                """;

        return this.chatClient.prompt()
                .user(userSpec -> userSpec
                        .text(promptTemplate)
                        .param("resume", resumeText)
                        .param("job", jobDescription)
                )
                .call()
                .content();
    }
}