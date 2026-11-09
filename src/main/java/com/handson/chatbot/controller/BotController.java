package com.handson.chatbot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.dialogflow.v2.*;
import com.google.protobuf.Value;
import com.handson.chatbot.enumCategories.JokeCategory;
import com.handson.chatbot.service.ChuckNorrisService;
import com.handson.chatbot.service.CityInfoService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.net.SocketTimeoutException;
import java.util.UUID;

@CrossOrigin(
        origins = {
                "http://localhost:3000",
                "https://bot.runmydocker-app.com"
        },
        allowedHeaders = "*",
        methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS }
)

@RestController
@RequestMapping("/bot")
public class BotController {

    private static final String PROJECT_ID    = "elad-chatbot-dsci";
    private static final String LANGUAGE_CODE = "en-US";

    private static final String CITY_LOOKUP_UNAVAILABLE = "City information is temporarily unavailable. Please try again later.";
    private static final String CITY_LOOKUP_TIMEOUT = "The city lookup timed out. Please try again.";

    private final ChuckNorrisService chuckNorrisService;
    private final CityInfoService   cityInfoService;

    public BotController(ChuckNorrisService chuckNorrisService,
                         CityInfoService   cityInfoService) {
        this.chuckNorrisService = chuckNorrisService;
        this.cityInfoService   = cityInfoService;
    }

    @GetMapping("/random")
    @ApiOperation(value = "Get a random Chuck Norris joke")
    public String getRandomJoke() {
        return chuckNorrisService.getRandomJoke();
    }

    @GetMapping("/random/{category}")
    @ApiOperation(value = "Get a random Chuck Norris joke by category")
    public String getJokeByCategory(
            @ApiParam(value = "Category of the joke", required = true, allowableValues = "ANIMAL, CAREER, CELEBRITY, DEV, FASHION, FOOD, HISTORY, MONEY, MOVIE, MUSIC, POLITICAL, RELIGION, SCIENCE, SPORT, TRAVEL")
            @PathVariable JokeCategory category) {
        return chuckNorrisService.getJokeByCategory(category.toString());
    }


    @GetMapping("/search")
    @ApiOperation(value = "Search Chuck Norris jokes by query")
    public String searchJokes(
            @ApiParam(value = "Search query for the joke", required = true)
            @RequestParam String query) {
        return chuckNorrisService.searchJokes(query);
    }

    @GetMapping("/city")
    @ApiOperation(value = "Get city info by name")
    public ResponseEntity<String> getCityInfo(
            @ApiParam(value = "Name of the city", required = true)
            @RequestParam String name) {
        try {
            JsonNode info = lookupCity(name);
            if (info == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("City information not found");
            }
            return ResponseEntity.ok(info.toPrettyString());
        } catch (ResponseStatusException exception) {
            return ResponseEntity.status(exception.getStatus()).body(exception.getReason());
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

            DetectIntentResponse response = sessionsClient.detectIntent(session, queryInput);
            QueryResult qr = response.getQueryResult();

            if (!qr.getAllRequiredParamsPresent()) {
                return ResponseEntity.ok(BotResponse.of(qr.getFulfillmentText()));
            }

            String intentName = qr.getIntent().getDisplayName();

            if ("GetJoke".equals(intentName)) {
                String category = qr.getParameters()
                        .getFieldsOrDefault("query", Value.newBuilder().build())
                        .getStringValue();
                String joke = category.isBlank()
                        ? chuckNorrisService.getRandomJoke()
                        : chuckNorrisService.getJokeByCategory(category);
                return ResponseEntity.ok(BotResponse.of(joke));
            }

            else if ("GetCityInfo".equals(intentName)) {
                String cityName = qr.getParameters()
                        .getFieldsOrDefault("name", Value.newBuilder().build())
                        .getStringValue();
                return ResponseEntity.ok(cityReply(cityName));
            }

            return ResponseEntity.ok(BotResponse.of(qr.getFulfillmentText()));
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
        private String fulfillmentText;
        private final String source = "BOT";
        public String getFulfillmentText() { return fulfillmentText; }
        public String getSource()          { return source; }
        public static BotResponse of(String text) {
            BotResponse r = new BotResponse();
            r.fulfillmentText = text;
            return r;
        }
    }
}