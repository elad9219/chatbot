package com.handson.chatbot;

import com.handson.chatbot.controller.BotController.BotRequest;
import com.handson.chatbot.controller.BotController.BotResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatbotApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper json;

	@Test
	void contextLoads() {
	}

	@ParameterizedTest
	@ValueSource(strings = {"http://localhost:3000", "https://eaichat.vercel.app"})
	void allowsChatFrontendToReadBackendHealth(String origin) throws Exception {
		mockMvc.perform(get("/actuator/health").header(HttpHeaders.ORIGIN, origin))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void healthResponseDoesNotExposeInternalDetails() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.components").doesNotExist())
				.andExpect(jsonPath("$.details").doesNotExist());
	}

	@Test
	void documentsTheChatEndpointInOpenApi() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openapi").isString())
				.andExpect(jsonPath("$.paths['/bot'].post").exists());
	}

	@ParameterizedTest
	@ValueSource(strings = {"http://localhost:3000", "https://eaichat.vercel.app"})
	void allowsChatFrontendToPost(String origin) throws Exception {
		mockMvc.perform(options("/bot")
						.header(HttpHeaders.ORIGIN, origin)
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/bot/random", "/bot/random/movie", "/bot/search", "/bot/city", "/cityinfo"
	})
	void retiredProviderEndpointsAreNotAvailable(String path) throws Exception {
		mockMvc.perform(get(path))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsAnUnapprovedBrowserOrigin() throws Exception {
		mockMvc.perform(options("/bot")
						.header(HttpHeaders.ORIGIN, "https://untrusted.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
				.andExpect(status().isForbidden());
	}

	@Test
	void preservesTheChatJsonContract() {
		BotRequest request = json.readValue(
				"{\"session\":\"test-session\",\"queryResult\":{\"queryText\":\"Find a movie by title\"}}",
				BotRequest.class);

		assertThat(request.getSession()).isEqualTo("test-session");
		assertThat(request.getQueryResult().getQueryText()).isEqualTo("Find a movie by title");

		var reply = json.readTree(json.writeValueAsString(BotResponse.of("Hello")));
		assertThat(reply.path("fulfillmentText").asString()).isEqualTo("Hello");
		assertThat(reply.path("source").asString()).isEqualTo("BOT");
		assertThat(reply.has("movies")).isFalse();
		assertThat(reply.has("suggestedReply")).isFalse();
	}
}