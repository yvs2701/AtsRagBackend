package com.example.atsragbackend.repository;

import com.example.atsragbackend.entity.MatchTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface MatchTaskRepository extends JpaRepository<MatchTask, String> {
    void deleteByCreatedAtBefore(Instant cutoffTime);
}