package com.handson.chatbot.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.dialogflow.v2.*;
import com.google.protobuf.Value;
import com.handson.chatbot.enumCategories.JokeCategory;
import com.handson.chatbot.service.ChuckNorrisService;
import com.handson.chatbot.service.CityInfoService;
import com.handson.chatbot.service.TmdbMovieService;
import com.handson.chatbot.service.TmdbMovieService.MovieSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@CrossOrigin(
        origins = {
                "http://localhost:3000",
                "https://bot.runmydocker-app.com"
        },
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}
)
@RestController
@RequestMapping("/bot")
public class BotController {

    private static final String PROJECT_ID = "elad-chatbot-dsci";
    private static final String LANGUAGE_CODE = "en-US";

    private static final String CITY_LOOKUP_UNAVAILABLE =
            "City information is temporarily unavailable. Please try again later.";
    private static final String CITY_LOOKUP_TIMEOUT =
            "The city lookup timed out. Please try again.";

    private final ChuckNorrisService chuckNorrisService;
    private final CityInfoService cityInfoService;
    private final TmdbMovieService movieService;

    public BotController(ChuckNorrisService chuckNorrisService,
                         CityInfoService cityInfoService,
                         TmdbMovieService movieService) {
        this.chuckNorrisService = chuckNorrisService;
        this.cityInfoService = cityInfoService;
        this.movieService = movieService;
    }

    @GetMapping("/random")
    @Operation(summary = "Get a random Chuck Norris joke")
    public String getRandomJoke() {
        return chuckNorrisService.getRandomJoke();
    }

    @GetMapping("/random/{category}")
    @Operation(summary = "Get a random Chuck Norris joke by category")
    public String getJokeByCategory(
            @Parameter(description = "Category of the joke", required = true)
            @PathVariable JokeCategory category) {
        return chuckNorrisService.getJokeByCategory(category.toString());
    }

    @GetMapping("/search")
    @Operation(summary = "Search Chuck Norris jokes by query")
    public String searchJokes(
            @Parameter(description = "Search query for the joke", required = true)
            @RequestParam String query) {
        return chuckNorrisService.searchJokes(query);
    }

    @GetMapping("/city")
    @Operation(summary = "Get city info by name")
    public ResponseEntity<String> getCityInfo(
            @Parameter(description = "Name of the city", required = true)
            @RequestParam String name) {
        try {
            JsonNode info = lookupCity(name);
            if (info == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("City information not found");
            }
            return ResponseEntity.ok(info.toPrettyString());
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatusCode()).body(exception.getReason());
        }
    }

    private JsonNode lookupCity(String cityName) {
        JsonNode info;
        try {
            info = cityInfoService.getCityInfo(cityName);
        } catch (ResourceAccessException exception) {
            if (exception.getMostSpecificCause() instanceof SocketTimeoutException) {
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, CITY_LOOKUP_TIMEOUT);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, CITY_LOOKUP_UNAVAILABLE);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, CITY_LOOKUP_UNAVAILABLE);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, CITY_LOOKUP_UNAVAILABLE);
        }

        if (info == null || info.isNull()) {
            return null;
        }
        if (!info.isArray()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, CITY_LOOKUP_UNAVAILABLE);
        }
        return info.isEmpty() ? null : info;
    }

    BotResponse cityReply(String cityName) {
        try {
            JsonNode info = lookupCity(cityName);
            return BotResponse.of(info == null ? "City not found. Try another city name." : info.toPrettyString());
        } catch (ResponseStatusException exception) {
            return BotResponse.of(exception.getReason());
        }
    }

    @PostMapping
    public ResponseEntity<BotResponse> handleBot(@RequestBody BotRequest request) throws Exception {
        String sessionId = request.getSession();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        try (SessionsClient sessionsClient = SessionsClient.create()) {
            SessionName session = SessionName.of(PROJECT_ID, sessionId);

            TextInput textInput = TextInput.newBuilder()
                    .setText(request.getQueryResult().getQueryText())
                    .setLanguageCode(LANGUAGE_CODE)
                    .build();

            QueryInput queryInput = QueryInput.newBuilder()
                    .setText(textInput)
                    .build();

            QueryParameters parameters = QueryParameters.newBuilder()
                    .addContexts(Context.newBuilder()
                            .setName(session + "/contexts/movie-demo")
                            .setLifespanCount(1))
                    .build();

            DetectIntentRequest intentRequest = DetectIntentRequest.newBuilder()
                    .setSession(session.toString())
                    .setQueryInput(queryInput)
                    .setQueryParams(parameters)
                    .build();

            return ResponseEntity.ok(replyTo(sessionsClient.detectIntent(intentRequest).getQueryResult()));
        }
    }

    BotResponse replyTo(QueryResult result) {
        if (!result.getAllRequiredParamsPresent()) {
            return BotResponse.of(result.getFulfillmentText());
        }

        switch (result.getIntent().getDisplayName()) {
            case "DiscoverShortComedies":
                return comedyReply(100, "Under 90 minutes");
            case "RefineShortComedies":
                return comedyReply(90, null);
            case "FindMovieByTitle":
                return titleReply(result.getParameters()
                        .getFieldsOrDefault("movieTitle", Value.getDefaultInstance())
                        .getStringValue().strip());
            case "GetJoke":
                String category = result.getParameters()
                        .getFieldsOrDefault("query", Value.getDefaultInstance())
                        .getStringValue();
                return BotResponse.of(category.isBlank()
                        ? chuckNorrisService.getRandomJoke()
                        : chuckNorrisService.getJokeByCategory(category));
            case "GetCityInfo":
                String cityName = result.getParameters()
                        .getFieldsOrDefault("name", Value.getDefaultInstance())
                        .getStringValue();
                return cityReply(cityName);
            default:
                return BotResponse.of(result.getFulfillmentText());
        }
    }

    private BotResponse comedyReply(int underMinutes, String suggestedReply) {
        return movieReply(() -> movieService.findComedies(underMinutes - 1),
                "Here are some comedies under " + underMinutes + " minutes.",
                BotResponse.of("I couldn't find any comedies under " + underMinutes + " minutes."),
                suggestedReply);
    }

    private BotResponse titleReply(String title) {
        if (title.isEmpty()) {
            return BotResponse.of("What movie title should I look up?");
        }
        if (title.length() > 150) {
            return BotResponse.of("Please enter a shorter movie title.");
        }

        return movieReply(() -> movieService.findByTitle(title),
                "Here are the movie matches I found.",
                new BotResponse("I couldn't find a movie with that title. Try a different title.",
                        null, "Find a movie by title"),
                null);
    }

    private BotResponse movieReply(Supplier<List<MovieSummary>> lookup, String successText,
                                   BotResponse emptyReply, String suggestedReply) {
        try {
            List<MovieSummary> movies = lookup.get();
            return movies.isEmpty() ? emptyReply : new BotResponse(successText, movies, suggestedReply);
        } catch (HttpClientErrorException.TooManyRequests exception) {
            return BotResponse.of("The movie service is busy. Please try again in a moment.");
        } catch (RestClientException | IllegalStateException exception) {
            return BotResponse.of("The movie service is temporarily unavailable. Please try again later.");
        }
    }

    public static class BotRequest {
        private String session;
        private RequestQueryResult queryResult;

        public String getSession() { return session; }
        public void setSession(String session) { this.session = session; }
        public RequestQueryResult getQueryResult() { return queryResult; }
        public void setQueryResult(RequestQueryResult qr) { this.queryResult = qr; }
    }

    public static class RequestQueryResult {
        private String queryText;

        public String getQueryText() { return queryText; }
        public void setQueryText(String queryText) { this.queryText = queryText; }
    }

    public static class BotResponse {
        private final String fulfillmentText;
        private final String source = "BOT";
        private final List<MovieSummary> movies;
        private final String suggestedReply;

        private BotResponse(String fulfillmentText, List<MovieSummary> movies, String suggestedReply) {
            this.fulfillmentText = fulfillmentText;
            this.movies = movies;
            this.suggestedReply = suggestedReply;
        }

        public String getFulfillmentText() { return fulfillmentText; }
        public String getSource() { return source; }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<MovieSummary> getMovies() { return movies; }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getSuggestedReply() { return suggestedReply; }

        public static BotResponse of(String text) {
            return new BotResponse(text, null, null);
        }
    }
}