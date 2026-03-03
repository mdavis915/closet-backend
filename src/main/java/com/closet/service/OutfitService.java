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
            
            Wardrobe:
            %s
            
            Make sure the outfit strongly reflects the %s aesthetic in your suggestion and reasoning.
            Respond with a JSON object only, no markdown:
            {"top":"item description","bottom":"item description","shoes":"item description","reason":"why this outfit works for the %s vibe"}
            Use only items from the wardrobe list above.
        """, vibe, occasion, vibe, weather, wardrobeList, vibe, vibe);

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