package com.handson.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class ChuckNorrisService {

    private final RestTemplate rest = new RestTemplate();

    private static final String RANDOM_URL      = "https://api.chucknorris.io/jokes/random";
    private static final String BY_CATEGORY_URL = "https://api.chucknorris.io/jokes/random?category={category}";
    private static final String SEARCH_URL      = "https://api.chucknorris.io/jokes/search?query={query}";

    public String getRandomJoke() {
        JsonNode node = rest.getForObject(RANDOM_URL, JsonNode.class);
        return node.get("value").asText();
    }

    public String getJokeByCategory(String category) {
        try {
            // מנסה לקבל בדיחה בספר קטגוריה
            JsonNode node = rest.getForObject(BY_CATEGORY_URL, JsonNode.class, category);
            return node.get("value").asText();
        } catch (HttpClientErrorException.NotFound nf) {
            // אם אין קטגוריה כזו, מפעיל fallback לחיפוש חופשי
            return searchJokes(category);
        }
    }

    public String searchJokes(String query) {
        JsonNode node = rest.getForObject(SEARCH_URL, JsonNode.class, query);
        ArrayNode arr = (ArrayNode) node.get("result");
        if (arr == null || arr.isEmpty()) {
            return "No jokes found for \"" + query + "\"";
        }
        return arr.get(0).get("value").asText();
    }
}
