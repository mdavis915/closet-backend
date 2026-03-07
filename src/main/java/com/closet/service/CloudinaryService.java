package com.closet.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Map;

@Service
public class CloudinaryService {

    private final String cloudName  = System.getenv("CLOUDINARY_CLOUD_NAME");
    private final String apiKey     = System.getenv("CLOUDINARY_API_KEY");
    private final String apiSecret  = System.getenv("CLOUDINARY_API_SECRET");

    public String uploadBase64Image(String base64Image) {
        try {
            // Strip data URI prefix if present
            String data = base64Image.contains(",")
                    ? base64Image.split(",")[1]
                    : base64Image;

            String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";

            // Build multipart body manually
            String boundary = "----FormBoundary" + System.currentTimeMillis();
            String dataUri = "data:image/jpeg;base64," + data;

            String body =
                    "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"file\"\r\n\r\n" +
                            dataUri + "\r\n" +
                            "--" + boundary + "\r\n" +
                            "Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n" +
                            "closet_unsigned" + "\r\n" +
                            "--" + boundary + "--\r\n";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uploadUrl))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            // Parse secure_url from JSON response
            int urlStart = responseBody.indexOf("\"secure_url\":\"") + 14;
            int urlEnd = responseBody.indexOf("\"", urlStart);
            if (urlStart > 13 && urlEnd > urlStart) {
                return responseBody.substring(urlStart, urlEnd);
            }

            throw new RuntimeException("Could not parse Cloudinary response: " + responseBody);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload image: " + e.getMessage(), e);
        }
    }
}