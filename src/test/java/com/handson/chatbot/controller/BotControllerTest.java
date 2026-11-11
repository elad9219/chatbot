package com.handson.chatbot.controller;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import com.google.cloud.dialogflow.v2.Intent;
import com.google.cloud.dialogflow.v2.QueryResult;
import com.handson.chatbot.service.DialogflowService;
import com.handson.chatbot.service.TmdbMovieService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BotControllerTest {

    private final TmdbMovieService movies = mock(TmdbMovieService.class);
    private final DialogflowService dialogflow = mock(DialogflowService.class);
    private final BotController controller = new BotController(movies, dialogflow);

    @Test
    void routesDiscoveryAndRefinementWithExclusiveRuntimeLimits() {
        when(movies.findComedies(99)).thenReturn(List.of());
        when(movies.findComedies(89)).thenReturn(List.of());

        assertThat(controller.replyTo(movieIntent("DiscoverShortComedies"))
                .getFulfillmentText()).contains("under 100 minutes");
        assertThat(controller.replyTo(movieIntent("RefineShortComedies"))
                .getFulfillmentText()).contains("under 90 minutes");
        verify(movies).findComedies(99);
        verify(movies).findComedies(89);
    }

    @Test
    void handlesMovieProviderRateLimitsWithoutExposingDetails() {
        when(movies.findComedies(99)).thenThrow(HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Provider details",
                org.springframework.http.HttpHeaders.EMPTY,
                new byte[0],
                null));

        var response = controller.replyTo(movieIntent("DiscoverShortComedies"));

        assertThat(response.getFulfillmentText()).isEqualTo(
                "The movie service is busy. Please try again in a moment.");
        assertThat(response.getMovies()).isNull();
    }

    @Test
    void forwardsTheSessionAndReturnsTheDialogflowReply() throws Exception {
        when(dialogflow.detectIntent("test-session", "Hello")).thenReturn(
                QueryResult.newBuilder().setFulfillmentText("Welcome").build());

        var response = controller.handleBot(chatRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFulfillmentText()).isEqualTo("Welcome");
        verify(dialogflow, times(1)).detectIntent("test-session", "Hello");
    }

    @ParameterizedTest
    @EnumSource(value = StatusCode.Code.class, names = {
            "DEADLINE_EXCEEDED", "RESOURCE_EXHAUSTED", "UNAVAILABLE"
    })
    void handlesDialogflowFailuresWithoutRepeatingTheMessage(
            StatusCode.Code code) throws Exception {
        ApiException failure = mock(ApiException.class);
        StatusCode status = mock(StatusCode.class);
        when(status.getCode()).thenReturn(code);
        when(failure.getStatusCode()).thenReturn(status);
        when(dialogflow.detectIntent("test-session", "Hello")).thenThrow(failure);

        var response = controller.handleBot(chatRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        String expected = switch (code) {
            case DEADLINE_EXCEEDED -> "Your message may have been processed";
            case RESOURCE_EXHAUSTED -> "reached its usage limit";
            default -> "temporarily unavailable";
        };
        assertThat(response.getBody().getFulfillmentText()).contains(expected);
        verify(dialogflow, times(1)).detectIntent("test-session", "Hello");
        verifyNoInteractions(movies);
    }

    @Test
    void hidesCredentialInitializationDetails() throws Exception {
        when(dialogflow.detectIntent("test-session", "Hello"))
                .thenThrow(new IOException("Private credential path"));

        var response = controller.handleBot(chatRequest());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFulfillmentText()).isEqualTo(
                "The chat service could not connect. Please try again later.");
        verify(dialogflow, times(1)).detectIntent("test-session", "Hello");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GetJoke", "GetCityInfo"})
    void redirectsLegacyIntentsWithoutCallingMovieProviders(String intentName) {
        for (boolean complete : List.of(false, true)) {
            QueryResult result = movieIntent(intentName).toBuilder()
                    .setAllRequiredParamsPresent(complete)
                    .setFulfillmentText("Legacy provider response")
                    .build();

            assertThat(controller.replyTo(result).getFulfillmentText())
                    .contains("find a movie by title")
                    .doesNotContain("Legacy provider response");
        }
        verifyNoInteractions(movies);
    }

    private BotController.BotRequest chatRequest() {
        var query = new BotController.RequestQueryResult();
        query.setQueryText("Hello");
        var request = new BotController.BotRequest();
        request.setSession("test-session");
        request.setQueryResult(query);
        return request;
    }

    private QueryResult movieIntent(String intentName) {
        return QueryResult.newBuilder()
                .setAllRequiredParamsPresent(true)
                .setIntent(Intent.newBuilder().setDisplayName(intentName))
                .build();
    }
}