package com.handson.chatbot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.dialogflow.v2.*;
import com.google.protobuf.Value;
import com.handson.chatbot.service.ChuckNorrisService;
import com.handson.chatbot.service.CityInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/bot")
public class BotController {

    private static final String PROJECT_ID    = "elad-chatbot-dsci";
    private static final String LANGUAGE_CODE = "en-US";

    private final ChuckNorrisService chuckNorrisService;
    private final CityInfoService   cityInfoService;

    public BotController(ChuckNorrisService chuckNorrisService,
                         CityInfoService   cityInfoService) {
        this.chuckNorrisService = chuckNorrisService;
        this.cityInfoService   = cityInfoService;
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

            // אם הפרמטרים עדיין לא מלאים, מחזיר את ההנחיה שהגדרת ב־Dialogflow
            if (!qr.getAllRequiredParamsPresent()) {
                return ResponseEntity.ok(BotResponse.of(qr.getFulfillmentText()));
            }

            String intentName = qr.getIntent().getDisplayName();

            // 1. בדיחות
            if ("GetJoke".equals(intentName)) {
                String category = qr.getParameters()
                        .getFieldsOrDefault("query", Value.newBuilder().build())
                        .getStringValue();
                String joke = category.isBlank()
                        ? chuckNorrisService.getRandomJoke()
                        : chuckNorrisService.getJokeByCategory(category);
                return ResponseEntity.ok(BotResponse.of(joke));
            }

            // 2. פרטי עיר
            else if ("GetCityInfo".equals(intentName)) {
                String cityName = qr.getParameters()
                        .getFieldsOrDefault("name", Value.newBuilder().build())
                        .getStringValue();
                JsonNode info = cityInfoService.getCityInfo(cityName);

                if (info == null) {
                    return ResponseEntity.ok(BotResponse.of("City not found"));
                }

                ObjectMapper mapper = new ObjectMapper();
                // הפורמטינג היפה: שורה חדשה לכל שדה, בלי פסיקים בתוך המספר
                String pretty = mapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(info);

                return ResponseEntity.ok(BotResponse.of(pretty));
            }

            // ברירת מחדל: כל מה שהגדרת ב־Dialogflow
            return ResponseEntity.ok(BotResponse.of(qr.getFulfillmentText()));
        }
    }

    // --- DTOs ---

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
