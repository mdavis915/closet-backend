package com.closet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
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

        // Determine category — check category prefix first (most reliable),
        // then fall back to keyword matching in the description
        String category;
        if (descLower.startsWith("dress:") || descLower.startsWith("outerwear:")) {
            category = "dresses";
        } else if (descLower.startsWith("bottom:")) {
            category = "lower_body";
        } else if (descLower.startsWith("top:") || descLower.startsWith("accessory:") || descLower.startsWith("shoes:")) {
            category = "upper_body";
        } else {
            // Fallback keyword matching — be conservative to avoid false positives
            if (descLower.contains("dress") || descLower.contains("jumpsuit") || descLower.contains("romper")) {
                category = "dresses";
            } else if (descLower.contains("pants") || descLower.contains("jeans") ||
                    descLower.contains("skirt") || descLower.contains("shorts") ||  // "shorts" not "short"
                    descLower.contains("trousers") || descLower.contains("leggings")) {
                category = "lower_body";
            } else {
                category = "upper_body";
            }
        }

        log.info("Try-on: description='{}' category={}", garmentDescription, category);

        Map<String, Object> input = Map.of(
                "garm_img",    garmentUrl,
                "human_img",   humanUrl,
                "garment_des", garmentDescription,
                "category",    category,
                "crop",        true,
                "steps",       30,
                "seed",        42
        );

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
                if (output instanceof String) {
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