package com.closet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private final String cloudName = System.getenv("CLOUDINARY_CLOUD_NAME");
    private final String apiKey    = System.getenv("CLOUDINARY_API_KEY");
    private final String apiSecret = System.getenv("CLOUDINARY_API_SECRET");

    public String uploadBase64Image(String base64Image) {
        try {
            log.info("Cloudinary upload starting, cloudName={}", cloudName);

            String data = base64Image.contains(",")
                    ? base64Image.split(",")[1]
                    : base64Image;

            String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
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

            log.info("Cloudinary response status: {}", response.statusCode());
            log.info("Cloudinary response body: {}", responseBody.substring(0, Math.min(300, responseBody.length())));

            int urlStart = responseBody.indexOf("\"secure_url\":\"") + 14;
            int urlEnd = responseBody.indexOf("\"", urlStart);
            if (urlStart > 13 && urlEnd > urlStart) {
                String url = responseBody.substring(urlStart, urlEnd);
                log.info("Cloudinary upload successful: {}", url);
                return url;
            }

            throw new RuntimeException("Could not parse Cloudinary response: " + responseBody);
        } catch (Exception e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload image: " + e.getMessage(), e);
        }
    }
}