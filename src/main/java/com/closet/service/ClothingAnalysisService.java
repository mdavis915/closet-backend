package com.closet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Service
public class ClothingAnalysisService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();

    public Map<String, String> analyzeClothing(String base64Image) {
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", """
                            Analyze this clothing item and respond ONLY with a JSON object, no markdown:
                            {"category":"tops/bottoms/shoes/outerwear/accessories","color":"primary color","style":"casual/formal/sporty/streetwear/elegant","description":"brief description"}
                        """),
                                        Map.of("type", "image_url", "image_url",
                                                Map.of("url", "data:image/jpeg;base64," + base64Image))
                                )
                        )
                ),
                "max_tokens", 200
        );

        Map response = restClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        List choices = (List) response.get("choices");
        Map choice = (Map) choices.get(0);
        Map message = (Map) choice.get("message");
        String content = (String) message.get("content");

        try {
            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            return mapper.readValue(content, Map.class);
        } catch (Exception e) {
            return Map.of("error", "Could not parse response", "raw", content);
        }
    }
}