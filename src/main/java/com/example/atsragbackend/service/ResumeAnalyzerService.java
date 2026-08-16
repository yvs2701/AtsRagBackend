package com.example.atsragbackend.service;

import com.example.atsragbackend.dto.JobSearchQuery;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ResumeAnalyzerService {

    private final ChatClient chatClient;

    public ResumeAnalyzerService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public JobSearchQuery generateSearchQuery(String resumeText) {
        String promptTemplate = """
                Analyze the following resume text and extract job search titles.
                
                Provide:
                1. "searchQuery": The primary job title matching the candidate's core profile and skill set.
                2. "searchQueries": A list of 2-3 closely related or alternative job titles to expand search coverage.
                
                Resume Text:
                {resume}
                """;

        return this.chatClient.prompt()
                .user(userSpec -> userSpec
                        .text(promptTemplate)
                        .param("resume", resumeText)
                )
                .call()
                .entity(JobSearchQuery.class);
    }
}