package com.handson.chatbot.service;

import com.google.cloud.dialogflow.v2.DetectIntentRequest;
import com.google.cloud.dialogflow.v2.DetectIntentResponse;
import com.google.cloud.dialogflow.v2.QueryResult;
import com.google.cloud.dialogflow.v2.SessionsClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DialogflowServiceTest {

    @Test
    void configuresABoundedRpcWithoutRetries() {
        var settings = DialogflowService.clientSettings().detectIntentSettings();

        assertThat(settings.getRetryableCodes()).isEmpty();
        assertThat(settings.getRetrySettings().getInitialRpcTimeoutDuration())
                .isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.getRetrySettings().getMaxRpcTimeoutDuration())
                .isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void reusesTheClientWhileKeepingConversationRequestsSeparate() throws Exception {
        SessionsClient client = mock(SessionsClient.class);
        AtomicInteger creations = new AtomicInteger();
        QueryResult result = QueryResult.newBuilder()
                .setFulfillmentText("Hello").build();

        when(client.detectIntent(any(DetectIntentRequest.class)))
                .thenReturn(DetectIntentResponse.newBuilder()
                        .setQueryResult(result).build());

        DialogflowService service = new DialogflowService(() -> {
            creations.incrementAndGet();
            return client;
        });

        assertThat(creations.get()).isZero();
        assertThat(service.detectIntent("first-session", "Hello")).isEqualTo(result);
        assertThat(service.detectIntent("second-session", "Find a movie"))
                .isEqualTo(result);

        ArgumentCaptor<DetectIntentRequest> requests =
                ArgumentCaptor.forClass(DetectIntentRequest.class);
        verify(client, times(2)).detectIntent(requests.capture());

        var first = requests.getAllValues().get(0);
        var second = requests.getAllValues().get(1);

        assertThat(first.getSession()).endsWith("/sessions/first-session");
        assertThat(second.getSession()).endsWith("/sessions/second-session");
        assertThat(first.getQueryInput().getText().getText()).isEqualTo("Hello");
        assertThat(second.getQueryInput().getText().getText()).isEqualTo("Find a movie");
        assertThat(first.getQueryInput().getText().getLanguageCode()).isEqualTo("en-US");

        for (var request : requests.getAllValues()) {
            assertThat(request.getQueryParams().getContextsCount()).isEqualTo(1);
            assertThat(request.getQueryParams().getContexts(0).getName())
                    .isEqualTo(request.getSession() + "/contexts/movie-demo");
            assertThat(request.getQueryParams().getContexts(0).getLifespanCount())
                    .isEqualTo(1);
        }

        assertThat(creations.get()).isEqualTo(1);
        verify(client, never()).close();

        service.close();
        service.close();
        verify(client, times(1)).close();
    }

    @Test
    void doesNotRepeatAFailedDetectIntentCall() {
        SessionsClient client = mock(SessionsClient.class);
        when(client.detectIntent(any(DetectIntentRequest.class)))
                .thenThrow(new IllegalStateException("Simulated transport failure"));
        DialogflowService service = new DialogflowService(() -> client);

        try {
            assertThatThrownBy(() -> service.detectIntent("session", "Hello"))
                    .isInstanceOf(IllegalStateException.class);

            verify(client, times(1)).detectIntent(any(DetectIntentRequest.class));
        } finally {
            service.close();
        }
    }

    @Test
    void closingAnUnusedServiceDoesNotCreateAClient() {
        AtomicInteger creations = new AtomicInteger();
        DialogflowService service = new DialogflowService(() -> {
            creations.incrementAndGet();
            throw new IOException("Client must not be created");
        });

        service.close();

        assertThat(creations.get()).isZero();
        assertThatThrownBy(() -> service.detectIntent("session", "Hello"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Dialogflow client is closed");
    }
}