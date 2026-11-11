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
import java.util.ArrayList;
import java.util.List;

@Service
public class TmdbMovieService {

    private static final String DISCOVER_URL = "https://api.themoviedb.org/3/discover/movie";
    private static final String SEARCH_URL = "https://api.themoviedb.org/3/search/movie";
    private static final int COMEDY_GENRE_ID = 35;
    private static final int RESULT_LIMIT = 3;

    private final RestTemplate restTemplate;
    private final String accessToken;

    public TmdbMovieService(RestTemplate restTemplate,
                            @Value("${tmdb.read.access.token:}") String accessToken) {
        this.restTemplate = restTemplate;
        this.accessToken = accessToken.strip();
    }

    public List<MovieSummary> findComedies(int maxRuntimeMinutes) {
        if (maxRuntimeMinutes < 1) {
            throw new IllegalArgumentException("Maximum runtime must be positive");
        }
        URI uri = UriComponentsBuilder.fromUriString(DISCOVER_URL)
                .queryParam("with_genres", COMEDY_GENRE_ID)
                .queryParam("with_runtime.lte", maxRuntimeMinutes)
                .queryParam("include_adult", false)
                .queryParam("language", "en-US")
                .queryParam("sort_by", "popularity.desc")
                .build().toUri();

        return fetchMovies(uri);
    }

    public List<MovieSummary> findByTitle(String title) {
        URI uri = UriComponentsBuilder.fromUriString(SEARCH_URL)
                .queryParam("query", "{title}")
                .queryParam("include_adult", false)
                .queryParam("language", "en-US")
                .encode()
                .buildAndExpand(title)
                .toUri();
        return fetchMovies(uri);
    }

    private List<MovieSummary> fetchMovies(URI uri) {
        if (accessToken.isEmpty()) {
            throw new IllegalStateException("TMDB is not configured");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        JsonNode body = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<Void>(headers), JsonNode.class)
                .getBody();
        if (body == null || !body.path("results").isArray()) {
            throw new IllegalStateException("Unexpected TMDB movie response");
        }

        List<MovieSummary> movies = new ArrayList<>();
        for (JsonNode result : body.path("results")) {
            int id = result.path("id").asInt(0);
            String title = result.path("title").asString("");
            if (id <= 0 || title.isBlank()) {
                continue;
            }

            movies.add(new MovieSummary(id, title, result.path("overview").asString(""),
                    result.path("release_date").asString(""), result.path("poster_path").asString(null)));
            if (movies.size() == RESULT_LIMIT) {
                break;
            }
        }

        return List.copyOf(movies);
    }

    public static final class MovieSummary {
        public final int id;
        public final String title;
        public final String overview;
        public final String releaseDate;
        public final String posterPath;

        private MovieSummary(int id, String title, String overview, String releaseDate, String posterPath) {
            this.id = id;
            this.title = title;
            this.overview = overview;
            this.releaseDate = releaseDate;
            this.posterPath = posterPath;
        }
    }
}