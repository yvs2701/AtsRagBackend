package com.example.atsragbackend.service;

import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class ResumeParsingService {

    public String extractText(MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }

        Tika tika = new Tika();

        // Tika automatically detects the file type (PDF, DOCX, etc.)
        try (InputStream stream = file.getInputStream()) {
            return tika.parseToString(stream).trim();
        }
    }
}