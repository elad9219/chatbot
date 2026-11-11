package com.handson.chatbot.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.dialogflow.v2.QueryResult;
import com.google.protobuf.Value;
import com.handson.chatbot.service.DialogflowService;
import com.handson.chatbot.service.TmdbMovieService;
import com.handson.chatbot.service.TmdbMovieService.MovieSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@RestController
@RequestMapping("/bot")
public class BotController {

    private static final Logger log = LoggerFactory.getLogger(BotController.class);

    private static final String MOVIE_HELP =
            "I can help you find a movie by title or discover a short comedy. "
                    + "Try \"Find the movie The Matrix\" or \"Comedy under 100 minutes\".";

    private final TmdbMovieService movieService;
    private final DialogflowService dialogflowService;

    public BotController(TmdbMovieService movieService,
                         DialogflowService dialogflowService) {
        this.movieService = movieService;
        this.dialogflowService = dialogflowService;
    }

    @PostMapping
    public ResponseEntity<BotResponse> handleBot(@RequestBody BotRequest request) {
        String sessionId = request.getSession();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
        }

        QueryResult result;
        try {
            result = dialogflowService.detectIntent(
                    sessionId, request.getQueryResult().getQueryText());
        } catch (ApiException exception) {
            var code = exception.getStatusCode().getCode();
            log.warn("Dialogflow request failed with status {}", code);

            String message = switch (code) {
                case DEADLINE_EXCEEDED ->
                        "The chat service took too long to respond. "
                                + "Your message may have been processed and was not sent again. "
                                + "Reload the page to start a new conversation if needed.";
                case RESOURCE_EXHAUSTED ->
                        "The chat service is busy or has reached its usage limit. "
                                + "Please try again later.";
                default ->
                        "The chat service is temporarily unavailable. "
                                + "Your message was not sent again. Please try again later.";
            };
            return ResponseEntity.ok(BotResponse.of(message));
        } catch (IOException exception) {
            log.warn("Dialogflow client could not be initialized");
            return ResponseEntity.ok(BotResponse.of(
                    "The chat service could not connect. Please try again later."));
        }

        return ResponseEntity.ok(replyTo(result));
    }

    BotResponse replyTo(QueryResult result) {
        String intentName = result.getIntent().getDisplayName();
        if ("GetJoke".equals(intentName) || "GetCityInfo".equals(intentName)) {
            return BotResponse.of(MOVIE_HELP);
        }
        if (!result.getAllRequiredParamsPresent()) {
            return BotResponse.of(result.getFulfillmentText());
        }

        switch (intentName) {
            case "DiscoverShortComedies":
                return comedyReply(100, "Under 90 minutes");
            case "RefineShortComedies":
                return comedyReply(90, null);
            case "FindMovieByTitle":
                return titleReply(result.getParameters()
                        .getFieldsOrDefault("movieTitle", Value.getDefaultInstance())
                        .getStringValue().strip());
            default:
                return BotResponse.of(result.getFulfillmentText().isBlank()
                        ? MOVIE_HELP : result.getFulfillmentText());
        }
    }

    private BotResponse comedyReply(int underMinutes, String suggestedReply) {
        return movieReply(() -> movieService.findComedies(underMinutes - 1),
                "Here are some comedies under " + underMinutes + " minutes.",
                BotResponse.of(
                        "I couldn't find any comedies under " + underMinutes + " minutes."),
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
                new BotResponse(
                        "I couldn't find a movie with that title. Try a different title.",
                        null, "Find a movie by title"),
                null);
    }

    private BotResponse movieReply(Supplier<List<MovieSummary>> lookup,
                                   String successText,
                                   BotResponse emptyReply,
                                   String suggestedReply) {
        try {
            List<MovieSummary> movies = lookup.get();
            return movies.isEmpty()
                    ? emptyReply
                    : new BotResponse(successText, movies, suggestedReply);
        } catch (HttpClientErrorException.TooManyRequests exception) {
            return BotResponse.of(
                    "The movie service is busy. Please try again in a moment.");
        } catch (RestClientException | IllegalStateException exception) {
            return BotResponse.of(
                    "The movie service is temporarily unavailable. Please try again later.");
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

        private BotResponse(String fulfillmentText,
                            List<MovieSummary> movies,
                            String suggestedReply) {
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