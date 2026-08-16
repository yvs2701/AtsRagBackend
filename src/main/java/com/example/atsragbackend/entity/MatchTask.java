package com.example.atsragbackend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class MatchTask {

    @Id
    private String taskId;

    public enum TaskStatus {PROCESSING, SUCCESS, FAILED}

    @Enumerated(EnumType.STRING)
    private TaskStatus status;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String pdfText;

    @Column(columnDefinition = "TEXT")
    private String resultPayload;

    @Column(updatable = false)
    private Instant createdAt;

    public MatchTask() {
    }

    public MatchTask(String taskId, TaskStatus status, String pdfText) {
        this.taskId = taskId;
        this.status = status;
        this.pdfText = pdfText;
        this.createdAt = Instant.now();
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getPdfText() {
        return pdfText;
    }

    public String getResultPayload() {
        return resultPayload;
    }

    public void setResultPayload(String resultPayload) {
        this.resultPayload = resultPayload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}