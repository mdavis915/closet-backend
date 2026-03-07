package com.closet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
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

        // is_checked_crop = true means the garment is a BOTTOM (pants, skirt, etc.)
        // false means it's a TOP — this was inverted before, causing misclassification
        String descLower = garmentDescription.toLowerCase();
        boolean isBottom = descLower.contains("pant") || descLower.contains("jean") ||
                descLower.contains("skirt") || descLower.contains("bottom") ||
                descLower.contains("short") || descLower.contains("trouser") ||
                descLower.contains("legging");

        // Also check the category prefix we now send: "Bottom: ..."
        if (descLower.startsWith("bottom:")) {
            isBottom = true;
        }

        log.info("Try-on: description='{}' isBottom={}", garmentDescription, isBottom);

        Map<String, Object> input = Map.of(
                "garm_img",        garmentUrl,
                "human_img",       humanUrl,
                "garment_des",     garmentDescription,
                "is_checked",      true,
                "is_checked_crop", isBottom,
                "denoise_steps",   30,
                "seed",            42
        );

        Map<String, Object> requestBody = Map.of(
                "version", "c871bb9b046607b680449ecbae55fd8c6d945e0a1948644bf2361b3d021d3ff4",
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

        for (int i = 0; i < 30; i++) {
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