package com.handson.chatbot.controller;

import com.google.cloud.dialogflow.v2.Intent;
import com.google.cloud.dialogflow.v2.QueryResult;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.handson.chatbot.service.ChuckNorrisService;
import com.handson.chatbot.service.CityInfoService;
import com.handson.chatbot.service.TmdbMovieService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MovieTitleLookupTest {

    private final RestTemplate rest = new RestTemplate();
    private final MockRestServiceServer tmdb = MockRestServiceServer.bindTo(rest).build();
    private final BotController controller = new BotController(mock(ChuckNorrisService.class),
            mock(CityInfoService.class), new TmdbMovieService(rest, "test-token"));

    @Test
    void forwardsThePromptBeforeSearchingByTitle() {
        QueryResult pending = titleIntent().setFulfillmentText("What movie title should I look up?").build();

        assertThat(controller.replyTo(pending).getFulfillmentText()).isEqualTo(pending.getFulfillmentText());
        tmdb.verify();
    }

    @Test
    void encodesTheTitleAndReturnsMovieCards() {
        tmdb.expect(requestTo("https://api.themoviedb.org/3/search/movie"
                        + "?query=L%C3%A9on%20%2B%20Me%20%26%20You&include_adult=false&language=en-US"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("{\"results\":[{\"id\":1,\"title\":\"First match\"},"
                        + "{\"id\":2,\"title\":\"Second match\"}]}", MediaType.APPLICATION_JSON));

        var response = controller.replyTo(titleResult("  Léon + Me & You  "));

        assertThat(response.getMovies()).extracting(movie -> movie.title)
                .containsExactly("First match", "Second match");
        assertThat(response.getSuggestedReply()).isNull();
        tmdb.verify();
    }

    @Test
    void offersToStartAnotherSearchWhenNoTitleMatches() {
        tmdb.expect(requestTo("https://api.themoviedb.org/3/search/movie"
                        + "?query=Unknown&include_adult=false&language=en-US"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));

        var response = controller.replyTo(titleResult("Unknown"));

        assertThat(response.getFulfillmentText()).contains("couldn't find a movie");
        assertThat(response.getMovies()).isNull();
        assertThat(response.getSuggestedReply()).isEqualTo("Find a movie by title");
        tmdb.verify();
    }

    private QueryResult.Builder titleIntent() {
        return QueryResult.newBuilder().setIntent(Intent.newBuilder().setDisplayName("FindMovieByTitle"));
    }

    private QueryResult titleResult(String title) {
        return titleIntent().setAllRequiredParamsPresent(true)
                .setParameters(Struct.newBuilder().putFields("movieTitle",
                        Value.newBuilder().setStringValue(title).build()))
                .build();
    }
}