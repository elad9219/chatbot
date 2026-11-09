package com.handson.chatbot.service;

import com.handson.chatbot.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CityInfoServiceTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AppConfig.class, CityInfoService.class);

    @Test
    void sendsConfiguredKeyAndEncodesCityName() {
        contextRunner.withInitializer(context -> TestPropertyValues.of("CITY_API_KEY=test-city-key")
                        .applyTo(context.getEnvironment(), TestPropertyValues.Type.SYSTEM_ENVIRONMENT))
                .run(context -> {
                    MockRestServiceServer provider = MockRestServiceServer.bindTo(context.getBean(RestTemplate.class))
                            .build();
                    provider.expect(requestTo("https://api.api-ninjas.com/v1/city?name=S%C3%A3o%20Paulo"))
                            .andExpect(method(HttpMethod.GET))
                            .andExpect(header("X-Api-Key", "test-city-key"))
                            .andRespond(withSuccess("[{\"name\":\"São Paulo\"}]", MediaType.APPLICATION_JSON));

                    assertThat(context.getBean(CityInfoService.class).getCityInfo("São Paulo")
                            .path(0).path("name").asText()).isEqualTo("São Paulo");
                    provider.verify();
                });
    }

    @Test
    void missingKeyDoesNotSendAProviderRequest() {
        contextRunner.withPropertyValues("city.api.key=").run(context -> {
            MockRestServiceServer provider = MockRestServiceServer.bindTo(context.getBean(RestTemplate.class))
                    .build();

            assertThatThrownBy(() -> context.getBean(CityInfoService.class).getCityInfo("Paris"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("City lookup is not configured");
            provider.verify();
        });
    }
}