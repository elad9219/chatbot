package com.handson.chatbot.service;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.dialogflow.v2.Context;
import com.google.cloud.dialogflow.v2.DetectIntentRequest;
import com.google.cloud.dialogflow.v2.QueryInput;
import com.google.cloud.dialogflow.v2.QueryParameters;
import com.google.cloud.dialogflow.v2.QueryResult;
import com.google.cloud.dialogflow.v2.SessionName;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.cloud.dialogflow.v2.SessionsSettings;
import com.google.cloud.dialogflow.v2.TextInput;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
public class DialogflowService {

    private static final String PROJECT_ID = "elad-chatbot-dsci";
    private static final String LANGUAGE_CODE = "en-US";
    private static final Duration RPC_TIMEOUT = Duration.ofSeconds(10);

    private final ClientFactory clientFactory;
    private SessionsClient client;
    private boolean closed;

    @Autowired
    public DialogflowService(@Value("${dialogflow.credentials.json:}") String credentialsJson) {
        this(() -> SessionsClient.create(clientSettings(credentialsJson).build()));
    }

    DialogflowService(ClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    static SessionsSettings.Builder clientSettings() {
        SessionsSettings.Builder settings = SessionsSettings.newBuilder();
        settings.detectIntentSettings()
                .setSimpleTimeoutNoRetriesDuration(RPC_TIMEOUT);
        return settings;
    }

    static SessionsSettings.Builder clientSettings(String credentialsJson) throws IOException {
        SessionsSettings.Builder settings = clientSettings();
        if (!credentialsJson.isBlank()) {
            try (var input = new ByteArrayInputStream(
                    credentialsJson.getBytes(StandardCharsets.UTF_8))) {
                ServiceAccountCredentials credentials = ServiceAccountCredentials.fromStream(input);
                if (!PROJECT_ID.equals(credentials.getProjectId())
                        || !URI.create("https://oauth2.googleapis.com/token")
                        .equals(credentials.getTokenServerUri())) {
                    throw new IOException("Unsupported Dialogflow credential configuration");
                }
                settings.setCredentialsProvider(FixedCredentialsProvider.create(
                        credentials.createScoped(List.of("https://www.googleapis.com/auth/cloud-platform"))));
            } catch (IOException | IllegalArgumentException exception) {
                throw new IOException("Invalid Dialogflow credential configuration");
            }
        }
        return settings;
    }

    public QueryResult detectIntent(String sessionId, String text) throws IOException {
        SessionName session = SessionName.of(PROJECT_ID, sessionId);

        DetectIntentRequest request = DetectIntentRequest.newBuilder()
                .setSession(session.toString())
                .setQueryInput(QueryInput.newBuilder()
                        .setText(TextInput.newBuilder()
                                .setText(text)
                                .setLanguageCode(LANGUAGE_CODE)))
                .setQueryParams(QueryParameters.newBuilder()
                        .addContexts(Context.newBuilder()
                                .setName(session + "/contexts/movie-demo")
                                .setLifespanCount(1)))
                .build();

        return getClient().detectIntent(request).getQueryResult();
    }

    private synchronized SessionsClient getClient() throws IOException {
        if (closed) {
            throw new IllegalStateException("Dialogflow client is closed");
        }
        if (client == null) {
            client = clientFactory.create();
        }
        return client;
    }

    @PreDestroy
    public synchronized void close() {
        if (!closed) {
            closed = true;
            if (client != null) {
                client.close();
            }
        }
    }

    @FunctionalInterface
    interface ClientFactory {
        SessionsClient create() throws IOException;
    }
}