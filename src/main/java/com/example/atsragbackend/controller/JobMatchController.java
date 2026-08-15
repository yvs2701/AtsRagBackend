package com.example.atsragbackend.controller;

import com.example.atsragbackend.model.JsonMessageResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/job-match")
public class JobMatchController {
    @GetMapping(value="/hello", produces=MediaType.APPLICATION_JSON_VALUE)
    public JsonMessageResponse hello() {
        return new JsonMessageResponse("Hello World!");
    }
}
