package com.handson.chatbot.controller;


import com.fasterxml.jackson.databind.JsonNode;
import com.handson.chatbot.service.CityInfoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CityInfoController {

    private final CityInfoService cityInfoService;

    public CityInfoController(CityInfoService cityInfoService) {
        this.cityInfoService = cityInfoService;
    }

    @GetMapping("/cityinfo")
    public JsonNode getCityInfo(@RequestParam String cityName) {
        try {
            return cityInfoService.getCityInfo(cityName);
        } catch (Exception e) {
            // Handle the exception and return a meaningful response
            e.printStackTrace();
            return null;
        }
    }
}

