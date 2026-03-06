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
                "garm_img", garmentUrl,
                "human_img", humanUrl,
                "garment_des", garmentDescription
        );

        Map<String, Object> requestBody = Map.of(
                "version", "0513734a452173b8173e907e3a59d19a36266e55b48528559432bd21c7d7e985",
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
                List output = (List) poll.get("output");
                return (String) output.get(0);
            } else if ("failed".equals(status)) {
                throw new RuntimeException("Try-on failed");
            }
        }
        throw new RuntimeException("Try-on timed out");
    }
}