package com.soubhagya.devscout.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate =
            new RestTemplate();

    public String testGemini() {

        return "Gemini Service Working";
    }
}