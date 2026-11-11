package com.handson.chatbot.service;

import com.handson.chatbot.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TmdbMovieServiceTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AppConfig.class, TmdbMovieService.class);

    @ParameterizedTest
    @ValueSource(ints = {99, 89})
    void requestsReleasedRatedComediesAndReturnsAtMostThreeCards(int maxRuntime) {
        contextRunner.withInitializer(context -> TestPropertyValues.of(
                        "TMDB_READ_ACCESS_TOKEN=test-token"
                ).applyTo(
                        context.getEnvironment(),
                        TestPropertyValues.Type.SYSTEM_ENVIRONMENT
                ))
                .run(context -> {
                    MockRestServiceServer provider = MockRestServiceServer
                            .bindTo(context.getBean(RestTemplate.class))
                            .build();

                    provider.expect(method(HttpMethod.GET))
                            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                            .andExpect(queryParam("with_genres", "35"))
                            .andExpect(queryParam("with_runtime.gte", "40"))
                            .andExpect(queryParam(
                                    "with_runtime.lte", Integer.toString(maxRuntime)
                            ))
                            .andExpect(request -> {
                                String query = request.getURI().getQuery();
                                String today = LocalDate.now(ZoneOffset.UTC).toString();
                                String yesterday = LocalDate.now(ZoneOffset.UTC)
                                        .minusDays(1).toString();

                                assertThat(query).containsAnyOf(
                                        "primary_release_date.lte=" + today,
                                        "primary_release_date.lte=" + yesterday
                                );
                            })
                            .andExpect(queryParam("vote_count.gte", "100"))
                            .andExpect(queryParam("vote_average.gte", "6.0"))
                            .andExpect(queryParam("include_adult", "false"))
                            .andExpect(queryParam("include_video", "false"))
                            .andExpect(queryParam("sort_by", "popularity.desc"))
                            .andRespond(withSuccess("""
                                    {
                                      "results": [
                                        {
                                          "id": 1,
                                          "title": "First",
                                          "overview": "A comedy.",
                                          "release_date": "2000-05-12",
                                          "poster_path": "/first.jpg"
                                        },
                                        {"id": 2, "title": "Second"},
                                        {"id": 3, "title": "Third"},
                                        {"id": 4, "title": "Fourth"}
                                      ]
                                    }
                                    """, MediaType.APPLICATION_JSON));

                    var movies = context.getBean(TmdbMovieService.class)
                            .findComedies(maxRuntime);

                    assertThat(movies).extracting(movie -> movie.title)
                            .containsExactly("First", "Second", "Third");
                    assertThat(movies.get(0).posterPath).isEqualTo("/first.jpg");
                    assertThat(movies.get(0).releaseDate).isEqualTo("2000-05-12");
                    assertThat(movies.get(0).overview).isEqualTo("A comedy.");
                    provider.verify();
                });
    }

    @Test
    void rejectsRuntimeBelowFeatureLengthWithoutCallingTmdb() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer provider = MockRestServiceServer
                .bindTo(restTemplate).build();
        TmdbMovieService service = new TmdbMovieService(restTemplate, "test-token");

        assertThatThrownBy(() -> service.findComedies(39))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Maximum runtime must be at least 40 minutes");

        provider.verify();
    }

    @Test
    void requiresAConfiguredTokenBeforeCallingTmdb() {
        contextRunner.withPropertyValues("tmdb.read.access.token=").run(context -> {
            MockRestServiceServer provider = MockRestServiceServer
                    .bindTo(context.getBean(RestTemplate.class))
                    .build();

            assertThatThrownBy(() -> context.getBean(TmdbMovieService.class)
                    .findComedies(99))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("TMDB is not configured");

            provider.verify();
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TMDB_LIVE_TEST", matches = "true")
    void findsComediesAgainstLiveTmdb() {
        contextRunner.run(context -> {
            var movies = context.getBean(TmdbMovieService.class).findComedies(99);

            assertThat(movies).isNotEmpty().hasSizeLessThanOrEqualTo(3);
            assertThat(movies).allSatisfy(movie -> {
                assertThat(movie.title).isNotBlank();
                assertThat(LocalDate.parse(movie.releaseDate))
                        .isBeforeOrEqualTo(LocalDate.now(ZoneOffset.UTC));
            });
        });
    }
}