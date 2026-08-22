package com.example.atsragbackend.exception;

/**
 * Custom exception exclusively to initiate retries when an Apify job is still running.
 */
public class ApifyJobStillRunningException extends RuntimeException {
    public ApifyJobStillRunningException(String message) {
        super(message);
    }
}
