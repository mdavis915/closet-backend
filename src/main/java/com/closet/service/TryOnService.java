package com.closet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TryOnService {

    private static final Logger log = LoggerFactory.getLogger(TryOnService.class);

    @Value("${replicate.api.token}")
    private String apiToken;

    private final RestClient restClient = RestClient.create();

    public String tryOn(String humanImageBase64, String garmentImageBase64, String garmentDescription) {
        String humanUrl = "data:image/jpeg;base64," + humanImageBase64;
        String garmentUrl = "data:image/jpeg;base64," + garmentImageBase64;

        String descLower = garmentDescription.toLowerCase();

        // Determine garment slot — use separate fields instead of broken is_checked_crop
        String slot; // "top", "bottom", or "dress"
        if (descLower.startsWith("dress:") || descLower.contains("dress") || descLower.contains("jumpsuit")) {
            slot = "dress";
        } else if (descLower.startsWith("bottom:") || descLower.contains("pant") || descLower.contains("jean") ||
                descLower.contains("skirt") || descLower.contains("short") || descLower.contains("trouser") ||
                descLower.contains("legging")) {
            slot = "bottom";
        } else {
            slot = "top";
        }

        log.info("Try-on: description='{}' slot={}", garmentDescription, slot);

        // Build input — only populate the relevant slot
        Map<String, Object> input = new HashMap<>();
        input.put("model_image", humanUrl);
        input.put("garment_des", garmentDescription);
        input.put("denoise_steps", 30);
        input.put("seed", 42);

        if ("top".equals(slot)) {
            input.put("top_image", garmentUrl);
        } else if ("bottom".equals(slot)) {
            input.put("bottom_image", garmentUrl);
        } else {
            input.put("dress_image", garmentUrl);
        }

        Map<String, Object> requestBody = Map.of(
                "version", "906425dbca90663ff5427624839572cc56ea7d380343d13e2a4c4b09d3f0c30f",
                "input", input
        );

        Map response = restClient.post()
                .uri("https://api.replicate.com/v1/predictions")
                .header("Authorization", "Token " + apiToken)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        String predictionId = (String) response.get("id");
        log.info("Try-on prediction started: {}", predictionId);

        for (int i = 0; i < 40; i++) {
            try { Thread.sleep(3000); } catch (InterruptedException e) {}

            Map poll = restClient.get()
                    .uri("https://api.replicate.com/v1/predictions/" + predictionId)
                    .header("Authorization", "Token " + apiToken)
                    .retrieve()
                    .body(Map.class);

            String status = (String) poll.get("status");
            Object output = poll.get("output");

            log.info("Poll {}: status={} output={}", i, status, output);

            if ("succeeded".equals(status)) {
                if (output instanceof List) {
                    List outputList = (List) output;
                    if (!outputList.isEmpty()) return (String) outputList.get(0);
                } else if (output instanceof String) {
                    return (String) output;
                }
                throw new RuntimeException("Try-on succeeded but output was empty");
            } else if ("failed".equals(status) || "canceled".equals(status)) {
                Object error = poll.get("error");
                log.error("Try-on failed: {}", error);
                throw new RuntimeException("Try-on failed: " + error);
            }
        }
        throw new RuntimeException("Try-on timed out");
    }
}