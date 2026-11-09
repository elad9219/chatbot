package com.handson.chatbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.handson.chatbot.service.ChuckNorrisService;
import com.handson.chatbot.service.CityInfoService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotControllerCityTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CityInfoService cities = mock(CityInfoService.class);
    private final BotController controller = new BotController(mock(ChuckNorrisService.class), cities);

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
}