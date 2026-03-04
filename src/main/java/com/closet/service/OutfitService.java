package com.closet.service;

import com.closet.model.ClothingItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OutfitService {

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();

    public String generateOutfit(List<ClothingItem> wardrobe, String occasion, String vibe, String weather) {
        String wardrobeList = wardrobe.stream()
                .map(item -> String.format("ID:%d - %s %s (%s)", item.getId(), item.getColor(), item.getCategory(), item.getStyle()))
                .collect(Collectors.joining("\n"));

        String prompt = String.format("""
            You are a personal stylist specializing in the %s aesthetic.
            Suggest a complete outfit for the following:
            
            Occasion: %s
            Vibe/Aesthetic: %s
            Weather: %s
            
            %s
            
            Make sure the outfit strongly reflects the %s aesthetic.
            You MUST respond with ONLY a valid JSON object, no markdown, no extra text, like this:
            {
              "name": "outfit name",
              "pieces": [
                {"name": "White Linen Shirt", "color": "white", "description": "tucked in, sleeves rolled"},
                {"name": "Navy Trousers", "color": "navy", "description": "tailored, straight leg"},
                {"name": "White Sneakers", "color": "white", "description": "clean, minimal"}
              ],
              "stylistNote": "why this outfit works"
            }
        """,
                        vibe, occasion, vibe, weather,
                        wardrobe.isEmpty()
                                ? "No wardrobe provided — suggest general outfit items that fit the aesthetic."
                                : "Use only items from this wardrobe:\n" + wardrobeList,
                        vibe);

        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "max_tokens", 300
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
        return (String) message.get("content");
    }
}