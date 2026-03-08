package com.closet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
public class RecreateService {

    @Value("${OPENAI_API_KEY}")
    private String openAiKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    /**
     * Analyzes an inspo outfit image against the user's wardrobe summary.
     * Returns a structured RecreateResult JSON string.
     */
    public String analyzeRecreate(String inspoBase64, String wardrobeSummary) throws Exception {

        String systemPrompt = """
            You are a personal stylist AI. The user will give you:
            1. An inspiration outfit photo (base64)
            2. A summary of their wardrobe (text list)

            Your job is to break down the outfit into pieces and check if the user owns something similar.

            Respond ONLY with valid JSON in this exact format (no markdown, no extra text):
            {
              "inspoSummary": "One sentence describing the overall outfit vibe and aesthetic",
              "vibe": "2-3 word vibe label e.g. Quiet Luxury or Street Casual",
              "ownedCount": <number of pieces they own>,
              "totalCount": <total pieces in the outfit>,
              "matches": [
                {
                  "piece": "Top",
                  "description": "What the inspo outfit has for this piece",
                  "color": "color name",
                  "owned": true or false,
                  "matchedItemId": <id number if owned, or null if not>,
                  "missingSuggestion": "Short suggestion of what to buy if not owned (null if owned)"
                }
              ]
            }

            Rules:
            - Break down into logical pieces: Top, Bottoms, Shoes, Outerwear, Bag, Accessory etc (only include pieces visible in the photo)
            - For owned=true, find the BEST matching item from the wardrobe summary by category and color similarity. Use its [id:X] value as matchedItemId.
            - If no close match exists for a piece, set owned=false
            - missingSuggestion should be a short, specific shopping suggestion like "Look for a fitted white Oxford shirt" — not generic
            - inspoSummary should be evocative and specific, not generic
            """;

        String userContent = String.format("""
            Here is my wardrobe:
            %s

            Please analyze the outfit in the photo and tell me what I own vs what I'm missing.
            """, wardrobeSummary.isEmpty() ? "(empty wardrobe)" : wardrobeSummary);

        // Build GPT-4o request with vision
        Map<String, Object> imageContent = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:image/jpeg;base64," + inspoBase64, "detail", "low")
        );
        Map<String, Object> textContent = Map.of("type", "text", "text", userContent);

        Map<String, Object> userMessage = Map.of(
                "role", "user",
                "content", List.of(imageContent, textContent)
        );

        Map<String, Object> body = Map.of(
                "model", "gpt-4o",
                "max_tokens", 1200,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        userMessage
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OpenAI error: " + response.statusCode() + " " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        String content = root.path("choices").get(0).path("message").path("content").asText();

        // Strip markdown fences if present
        content = content.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        // Validate it's parseable JSON
        mapper.readTree(content);

        return content;
    }
}