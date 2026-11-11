package com.handson.chatbot.controller;

import tools.jackson.databind.json.JsonMapper;
import com.google.cloud.dialogflow.v2.Intent;
import com.google.cloud.dialogflow.v2.QueryResult;
import com.handson.chatbot.service.ChuckNorrisService;
import com.handson.chatbot.service.CityInfoService;
import com.handson.chatbot.service.TmdbMovieService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotControllerTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final CityInfoService cities = mock(CityInfoService.class);
    private final TmdbMovieService movies = mock(TmdbMovieService.class);
    private final BotController controller = new BotController(mock(ChuckNorrisService.class), cities, movies);

    @Test
    void preservesCityResultsInBothPaths() throws Exception {
        var results = mapper.readTree("[{\"name\":\"Paris\",\"country\":\"FR\"}]");
        when(cities.getCityInfo("Paris")).thenReturn(results);

        var response = controller.getCityInfo("Paris");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(response.getBody())).isEqualTo(results);
        assertThat(mapper.readTree(controller.cityReply("Paris").getFulfillmentText())).isEqualTo(results);
    }

    @Test
    void emptyArrayProducesNotFoundInsteadOfBlankChat() {
        when(cities.getCityInfo("Unknown")).thenReturn(mapper.createArrayNode());

        assertThat(controller.getCityInfo("Unknown").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(controller.cityReply("Unknown").getFulfillmentText()).startsWith("City not found.");
    }

    @Test
    void providerErrorDoesNotExposeUpstreamDetails() {
        when(cities.getCityInfo("Paris")).thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        var response = controller.getCityInfo("Paris");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isEqualTo("City information is temporarily unavailable. Please try again later.");
        assertThat(controller.cityReply("Paris").getFulfillmentText()).isEqualTo(response.getBody());
    }

    @Test
    void distinguishesProviderTimeoutFromMissingCity() {
        when(cities.getCityInfo("Paris")).thenThrow(
                new ResourceAccessException("Provider request failed", new SocketTimeoutException()));

        var response = controller.getCityInfo("Paris");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(controller.cityReply("Paris").getFulfillmentText())
                .isEqualTo("The city lookup timed out. Please try again.");
    }

    @Test
    void routesDiscoveryAndRefinementWithExclusiveRuntimeLimits() {
        when(movies.findComedies(99)).thenReturn(List.of());
        when(movies.findComedies(89)).thenReturn(List.of());

        assertThat(controller.replyTo(movieIntent("DiscoverShortComedies")).getFulfillmentText())
                .contains("under 100 minutes");
        assertThat(controller.replyTo(movieIntent("RefineShortComedies")).getFulfillmentText())
                .contains("under 90 minutes");
        verify(movies).findComedies(99);
        verify(movies).findComedies(89);
    }

    @Test
    void handlesMovieProviderRateLimitsWithoutExposingDetails() {
        when(movies.findComedies(99)).thenThrow(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Provider details",
                        org.springframework.http.HttpHeaders.EMPTY, new byte[0], null));

        var response = controller.replyTo(movieIntent("DiscoverShortComedies"));

        assertThat(response.getFulfillmentText()).isEqualTo("The movie service is busy. Please try again in a moment.");
        assertThat(response.getMovies()).isNull();
    }

    private QueryResult movieIntent(String intentName) {
        return QueryResult.newBuilder()
                .setAllRequiredParamsPresent(true)
                .setIntent(Intent.newBuilder().setDisplayName(intentName))
                .build();
    }
}