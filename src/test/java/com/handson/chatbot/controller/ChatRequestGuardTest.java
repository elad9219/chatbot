package com.handson.chatbot.controller;

import tools.jackson.databind.json.JsonMapper;
import com.handson.chatbot.controller.BotController.BotResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatRequestGuardTest {

    private static final String SESSION = "d4a75f43-e6c7-43b6-a38e-69de40b61b29";

    private final AtomicLong clock = new AtomicLong();
    private final JsonMapper json = JsonMapper.builder().build();
    private final RecordingBotController controller = new RecordingBotController();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ChatRequestGuard(clock::get)).build();

    @Test
    void rejectsInvalidRequestsBeforeInvokingTheChatHandler() throws Exception {
        mvc.perform(post("/bot").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        send("  ", SESSION).andExpect(status().isBadRequest());
        send("x".repeat(257), SESSION).andExpect(status().isBadRequest());
        send("Find a movie", "wrong/path").andExpect(status().isBadRequest());
        send("Find a movie", "x".repeat(37)).andExpect(status().isBadRequest());
        send("x".repeat(4096), SESSION).andExpect(status().isPayloadTooLarge());
        assertThat(controller.calls).isZero();
    }

    @Test
    void acceptsTheUnicodeCharacterBoundaryAndOptionalSession() throws Exception {
        send("🎬".repeat(256), SESSION).andExpect(status().isOk());
        mvc.perform(post("/bot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"queryResult\":{\"queryText\":\"Find a movie\"}}"))
                .andExpect(status().isOk());
        assertThat(controller.calls).isEqualTo(2);
    }

    @Test
    void capsRequestsAcrossSessionsAndAllowsAnotherAfterTheWindow() throws Exception {
        for (int i = 0; i < 60; i++) {
            send("Find a movie", "session-" + i).andExpect(status().isOk());
        }
        clock.set(SECONDS.toNanos(59));
        send("Find a movie", "different-session")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"))
                .andExpect(jsonPath("$.fulfillmentText").value("The chat is busy. Wait a moment and try again."));
        assertThat(controller.calls).isEqualTo(60);

        clock.set(SECONDS.toNanos(60));
        send("Find a movie", "different-session").andExpect(status().isOk());
        assertThat(controller.calls).isEqualTo(61);
    }

    private ResultActions send(String text, String session) throws Exception {
        return mvc.perform(post("/bot").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("session", session, "queryResult", Map.of("queryText", text)))));
    }

    private static class RecordingBotController extends BotController {
        private int calls;

        RecordingBotController() {
            super(null, null, null);
        }

        @Override
        public ResponseEntity<BotResponse> handleBot(@RequestBody BotRequest request) {
            calls++;
            return ResponseEntity.ok(BotResponse.of("Reached chat handler"));
        }
    }
}