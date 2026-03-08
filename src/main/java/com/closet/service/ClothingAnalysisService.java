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

    public List<Map<String, Object>> analyzeClothing(String base64Image) {
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o",
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", List.of(
                                        Map.of("type", "text", "text", """
                Look at this image carefully. Identify EVERY separate, individual clothing item visible.
                Each item must be its own separate object in the array — never combine multiple items into one description.

                Respond ONLY with a JSON array, no markdown, no extra text:
                [
                  {
                    "category": "Top" | "Bottom" | "Shoes" | "Outerwear" | "Dress" | "Accessory",
                    "color": "primary color name, single lowercase word (e.g. navy, cream, black)",
                    "style": "casual" | "formal" | "sporty" | "streetwear" | "elegant",
                    "season": "Spring" | "Summer" | "Fall" | "Winter" | "All",
                    "description": "specific description of this single item only (e.g. 'Oversized cream linen shirt')",
                    "cropBox": {
                      "topPercent": <number 0-100>,
                      "leftPercent": <number 0-100>,
                      "widthPercent": <number 0-100>,
                      "heightPercent": <number 0-100>
                    }
                  }
                ]

                cropBox rules:
                - Express the bounding box of each individual clothing item as percentages of the full image dimensions
                - Add ~5% padding so the item is not cut off at the edges
                - For a full-body photo: top (shirt/jacket) is roughly top 0-50%, bottom (pants/skirt) roughly 40-100%, shoes roughly 80-100%
                - For a flat-lay or single item photo: cropBox should cover nearly the full image (e.g. topPercent:2, leftPercent:2, widthPercent:96, heightPercent:96)
                - Never return null for cropBox — always estimate

                Other rules:
                - If only one item is visible, still return an array with one object
                - Never describe multiple items in a single description field
                - Maximum 6 items per photo
                - Only include clearly visible items
            """),
                                        Map.of("type", "image_url", "image_url",
                                                Map.of("url", "data:image/jpeg;base64," + base64Image,
                                                        "detail", "low"))
                                )
                        )
                ),
                "max_tokens", 800
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
            try {
                Map<String, Object> single = mapper.readValue(content, Map.class);
                return List.of(single);
            } catch (Exception ex) {
                return List.of(Map.of(
                        "category", "Top",
                        "color", "unknown",
                        "style", "casual",
                        "season", "All",
                        "description", "Unidentified item",
                        "cropBox", Map.of("topPercent", 5, "leftPercent", 5, "widthPercent", 90, "heightPercent", 90)
                ));
            }
        }
    }
}