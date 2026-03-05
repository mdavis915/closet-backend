package com.closet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Map<String, String>> analyzeClothing(String base64Image) {
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", """
                Look at this image carefully. Identify EVERY separate, individual clothing item visible.
                Each item must be its own separate object in the array — never combine multiple items into one description.
                For example, if you see a white shirt AND a floral shirt, that is TWO separate objects.
                
                Respond ONLY with a JSON array, no markdown:
                [
                  {"category":"Top/Bottom/Shoes/Outerwear/Dress/Accessory","color":"primary color","style":"casual/formal/sporty/streetwear/elegant","description":"specific description of this single item only"}
                ]
                
                If only one item is visible, still return an array with one object.
                Never describe multiple items in a single description field.
            """),
                                        Map.of("type", "image_url", "image_url",
                                                Map.of("url", "data:image/jpeg;base64," + base64Image))
                                )
                        )
                ),
                "max_tokens", 400
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
        String content = ((String) message.get("content"))
                .replace("```json", "").replace("```", "").trim();

        try {
            return mapper.readValue(content, List.class);
        } catch (Exception e) {
            // fallback: wrap whatever came back as a single item
            try {
                Map<String, String> single = mapper.readValue(content, Map.class);
                return List.of(single);
            } catch (Exception ex) {
                return List.of(Map.of("category", "Top", "color", "unknown",
                        "style", "casual", "description", "Unidentified item"));
            }
        }
    }
}