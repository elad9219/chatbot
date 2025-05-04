package com.handson.chatbot.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JokeResponse {
    private String value;

    // Getter and Setter
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}



