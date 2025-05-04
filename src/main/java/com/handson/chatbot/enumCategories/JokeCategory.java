package com.handson.chatbot.enumCategories;

public enum JokeCategory {
    ANIMAL,
    CAREER,
    CELEBRITY,
    DEV,
    FASHION,
    FOOD,
    HISTORY,
    MONEY,
    MOVIE,
    MUSIC,
    POLITICAL,
    RELIGION,
    SCIENCE,
    SPORT,
    TRAVEL;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}

