package com.closet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.Map;

@Service
public class WeatherService {

    @Value("${weather.api.key}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();

    public Map<String, Object> getWeather(String city) {
        String url = "https://api.openweathermap.org/data/2.5/weather?q=" + city + "&appid=" + apiKey + "&units=imperial";
        return restClient.get()
                .uri(url)
                .retrieve()
                .body(Map.class);
    }

    public String getWeatherSummary(String city) {
        Map<String, Object> weather = getWeather(city);
        Map<String, Object> main = (Map<String, Object>) weather.get("main");
        java.util.List<Map<String, Object>> weatherList = (java.util.List<Map<String, Object>>) weather.get("weather");

        double temp = ((Number) main.get("temp")).doubleValue();
        String description = (String) weatherList.get(0).get("description");

        return String.format("%.0f°F, %s", temp, description);
    }
}