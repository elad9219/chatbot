package com.handson.chatbot.service;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Service
public class CityInfoService {

    private static final String API_URL = "https://api.api-ninjas.com/v1/city";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public CityInfoService(RestTemplate restTemplate, @Value("${city.api.key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    public JsonNode getCityInfo(String cityName) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("City lookup is not configured");
        }

        URI uri = UriComponentsBuilder.fromUriString(API_URL)
                .queryParam("name", "{cityName}")
                .encode()
                .buildAndExpand(cityName)
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Api-Key", apiKey);

        return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<Void>(headers), JsonNode.class)
                .getBody();
    }
}