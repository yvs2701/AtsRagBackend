package com.example.atsragbackend.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class ResumeParsingService {

    private final Tika tika = new Tika();

    public String extractText(MultipartFile file) throws IOException, TikaException {
        return tika.parseToString(file.getInputStream());
    }
}