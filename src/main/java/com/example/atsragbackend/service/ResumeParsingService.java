package com.example.atsragbackend.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ResumeParsingService {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(ResumeParsingService.class);

    private final Tika tika = new Tika();

    public String extractText(MultipartFile file) throws IOException, TikaException {
        log.debug("Extracting text from file: {}", file.getOriginalFilename());
        return tika.parseToString(file.getInputStream());
    }
}