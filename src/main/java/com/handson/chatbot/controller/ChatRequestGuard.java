package com.handson.chatbot.controller;

import com.handson.chatbot.controller.BotController.BotRequest;
import com.handson.chatbot.controller.BotController.BotResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@RestControllerAdvice(assignableTypes = BotController.class)
public class ChatRequestGuard extends RequestBodyAdviceAdapter {

    private static final int MAX_BODY_BYTES = 4_096;
    private static final int MAX_MESSAGE_CHARACTERS = 256;
    private static final int MAX_REQUESTS_PER_MINUTE = 60;
    private static final long WINDOW_NANOS = SECONDS.toNanos(60);
    private static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9_-]{1,36}");

    private final LongSupplier nanoTime;
    private final ArrayDeque<Long> acceptedAt = new ArrayDeque<>();

    public ChatRequestGuard() {
        this(System::nanoTime);
    }

    ChatRequestGuard(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == BotRequest.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
        if (input.getHeaders().getContentLength() > MAX_BODY_BYTES) {
            throw tooLarge();
        }
        byte[] body = input.getBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw tooLarge();
        }

        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return new ByteArrayInputStream(body);
            }

            @Override
            public HttpHeaders getHeaders() {
                return input.getHeaders();
            }
        };
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        BotRequest request = (BotRequest) body;
        String text = request.getQueryResult() == null ? null : request.getQueryResult().getQueryText();
        if (text == null || text.isBlank()) {
            throw new RejectedChat(HttpStatus.BAD_REQUEST, "Enter a message before sending.", 0);
        }
        if (text.codePointCount(0, text.length()) > MAX_MESSAGE_CHARACTERS) {
            throw new RejectedChat(HttpStatus.BAD_REQUEST, "Keep your message to 256 characters or fewer.", 0);
        }

        String session = request.getSession();
        if (session != null && !session.isBlank() && !SESSION_ID.matcher(session).matches()) {
            throw new RejectedChat(HttpStatus.BAD_REQUEST, "The chat session is invalid. Reload to start a new chat.", 0);
        }

        long retryAfter = reserveRequest();
        if (retryAfter > 0) {
            throw new RejectedChat(HttpStatus.TOO_MANY_REQUESTS,
                    "The chat is busy. Wait a moment and try again.", retryAfter);
        }
        return request;
    }

    private synchronized long reserveRequest() {
        long now = nanoTime.getAsLong();
        while (!acceptedAt.isEmpty() && now - acceptedAt.peekFirst() >= WINDOW_NANOS) {
            acceptedAt.removeFirst();
        }
        if (acceptedAt.size() >= MAX_REQUESTS_PER_MINUTE) {
            long remaining = WINDOW_NANOS - (now - acceptedAt.peekFirst());
            return NANOSECONDS.toSeconds(remaining - 1) + 1;
        }
        acceptedAt.addLast(now);
        return 0;
    }

    private static RejectedChat tooLarge() {
        return new RejectedChat(HttpStatus.CONTENT_TOO_LARGE, "The chat request is too large.", 0);
    }

    @ExceptionHandler(RejectedChat.class)
    ResponseEntity<BotResponse> reject(RejectedChat exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(exception.status);
        if (exception.retryAfter > 0) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfter));
        }
        return response.body(BotResponse.of(exception.getMessage()));
    }

    static class RejectedChat extends RuntimeException {
        private final HttpStatus status;
        private final long retryAfter;

        private RejectedChat(HttpStatus status, String message, long retryAfter) {
            super(message);
            this.status = status;
            this.retryAfter = retryAfter;
        }
    }
}