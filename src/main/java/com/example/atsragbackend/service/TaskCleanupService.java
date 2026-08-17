package com.example.atsragbackend.service;

import com.example.atsragbackend.repository.MatchTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Configuration
@EnableScheduling
public class TaskCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TaskCleanupService.class);
    private final MatchTaskRepository taskRepository;

    public TaskCleanupService(MatchTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // Runs every 15 minutes (9,00,000 ms)
    @Scheduled(fixedRate = 900000)
    @Transactional
    public void cleanUpOrphanedTasks() {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        taskRepository.deleteByCreatedAtBefore(oneHourAgo);
        log.info("Executed routine cleanup for orphaned MatchTask records.");
    }
}