package com.handson.chatbot.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JokeSearchResponse {
    private List<JokeResponse> result;

    // Getter and Setter
    public List<JokeResponse> getResult() {
        return result;
    }

    public void setResult(List<JokeResponse> result) {
        this.result = result;
    }
}
