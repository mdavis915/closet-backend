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

        Map<String, Object> input = Map.of(
                "human_image", humanUrl,
                "garment_image", garmentUrl,
                "garment_description", garmentDescription,
                "hf_token", System.getenv("HF_TOKEN")
        );

        Map<String, Object> requestBody = Map.of(
                "version", "cc41d1b963023987ed2ddf26e9264efcc96ee076640115c303f95b0010f6a958",
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

            log.info("Poll {}: status={} output_type={} output={}", i, status,
                    output == null ? "null" : output.getClass().getSimpleName(), output);

            if ("succeeded".equals(status)) {
                if (output instanceof List) {
                    List outputList = (List) output;
                    if (!outputList.isEmpty()) {
                        return (String) outputList.get(0);
                    }
                } else if (output instanceof String) {
                    return (String) output;
                } else if (output != null) {
                    log.warn("Unexpected output type: {} value: {}", output.getClass(), output);
                    return output.toString();
                }
                throw new RuntimeException("Try-on succeeded but output was empty or null");
            } else if ("failed".equals(status) || "canceled".equals(status)) {
                Object error = poll.get("error");
                log.error("Try-on failed: {}", error);
                throw new RuntimeException("Try-on failed: " + error);
            }
        }
        throw new RuntimeException("Try-on timed out after 2 minutes");
    }
}