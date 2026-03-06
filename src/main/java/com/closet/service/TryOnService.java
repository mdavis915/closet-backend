package com.closet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Service
public class TryOnService {

    @Value("${replicate.api.token}")
    private String apiToken;

    private final RestClient restClient = RestClient.create();

    public String tryOn(String humanImageBase64, String garmentImageBase64, String garmentDescription) {
        String humanUrl = "data:image/jpeg;base64," + humanImageBase64;
        String garmentUrl = "data:image/jpeg;base64," + garmentImageBase64;

        Map<String, Object> input = Map.of(
                "human_image", humanUrl,
                "garment_image", garmentUrl,
                "garment_description", garmentDescription
        );

        Map<String, Object> requestBody = Map.of(
                "version", "cc41d1b963023987ed2ddf26e9264efcc96ee076640115c303f95b0010f6a958",
                "input", input
        );

        // Start the prediction
        Map response = restClient.post()
                .uri("https://api.replicate.com/v1/predictions")
                .header("Authorization", "Token " + apiToken)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        String predictionId = (String) response.get("id");

        // Poll until complete
        for (int i = 0; i < 30; i++) {
            try { Thread.sleep(2000); } catch (InterruptedException e) {}

            Map poll = restClient.get()
                    .uri("https://api.replicate.com/v1/predictions/" + predictionId)
                    .header("Authorization", "Token " + apiToken)
                    .retrieve()
                    .body(Map.class);

            String status = (String) poll.get("status");
            if ("succeeded".equals(status)) {
                Object output = poll.get("output");
                if (output instanceof List) {
                    return (String) ((List) output).get(0);
                } else {
                    return (String) output;
                }
            } else if ("failed".equals(status)) {
                throw new RuntimeException("Try-on failed");
            }
        }
        throw new RuntimeException("Try-on timed out");
    }
}