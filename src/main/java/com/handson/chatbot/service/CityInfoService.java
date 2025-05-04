package com.handson.chatbot.service;


import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class CityInfoService {

    private static final String API_URL = "https://api.api-ninjas.com/v1/city?name=";
    private static final String API_KEY = "CLsEYJt7myeMiBCzInEoKT4wy6a9lWdlRDfI6PzI"; 

    public JsonNode getCityInfo(String cityName) throws Exception {
        // Encode the city name to handle spaces and special characters
        String encodedCityName = URLEncoder.encode(cityName, StandardCharsets.UTF_8.toString());
        URL url = new URL(API_URL + encodedCityName);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("accept", "application/json");
        connection.setRequestProperty("X-Api-Key", API_KEY);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Failed to fetch city info: " + responseCode);
        }

        InputStream responseStream = connection.getInputStream();
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(responseStream);
    }
}

