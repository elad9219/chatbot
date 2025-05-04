package com.handson.chatbot.service;

import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.regex.Pattern;

@Service
public class AmazonService {

    public static final Pattern PRODUCT_PATTERN = Pattern.compile("<span class=\\\"a-size-medium a-color-base a-text-normal\\\">([^<]+)</span> </a> </h2></div><div class=\\\"a-section a-spacing-none a-spacing-top-micro\\\"><div class=\\\"a-row a-size-small\\\"><span aria-label=\\\"([^\\\"]+)\\\"><span.*<span class=\\\"a-offscreen\\\">([^<]+)</span>");


    public String searchProducts(String keyword) {
        return "Searched for:" + keyword;
    }

    private String getProductHtml(String keyword) throws IOException {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        MediaType mediaType = MediaType.parse("text/plain");
        RequestBody body = RequestBody.create(mediaType, "");
        Request request = new Request.Builder()
                .url("https://www.amazon.com/s?i=aps&k=ipod&ref=nb_sb_noss&url=search-alias%3Daps")
                .method("GET", body)
                .addHeader("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .addHeader("accept-language", "he,en;q=0.9")
                .addHeader("cookie", "aws-target-data=%7B%22support%22%3A%221%22%7D; aws-target-visitor-id=1721119502000-736188.44_0; aws-ubid-main=348-7025615-0067150; remember-account=true; aws-account-alias=995553441267; regStatus=registered; aws-userInfo=%7B%22arn%22%3A%22arn%3Aaws%3Aiam%3A%3A995553441267%3Auser%2Feladt%22%2C%22alias%22%3A%22995553441267%22%2C%22username%22%3A%22eladt%22%2C%22keybase%22%3A%22uQi5SpiPBlL%2B67tUh2kkXxZUzvf4VRPEFlhXPWymo%2FI%5Cu003d%22%2C%22issuer%22%3A%22http%3A%2F%2Fsignin.aws.amazon.com%2Fsignin%22%2C%22signinType%22%3A%22PUBLIC%22%7D; aws-userInfo-signed=eyJ0eXAiOiJKV1MiLCJrZXlSZWdpb24iOiJ1cy1lYXN0LTIiLCJhbGciOiJFUzM4NCIsImtpZCI6IjhiYWIzZWY0LWM1OTEtNGVkOS1hM2U1LTQ1NjQyNjE4MTdkYSJ9.eyJzdWIiOiI5OTU1NTM0NDEyNjciLCJzaWduaW5UeXBlIjoiUFVCTElDIiwiaXNzIjoiaHR0cDpcL1wvc2lnbmluLmF3cy5hbWF6b24uY29tXC9zaWduaW4iLCJrZXliYXNlIjoidVFpNVNwaVBCbEwrNjd0VWgya2tYeFpVenZmNFZSUEVGbGhYUFd5bW9cL0k9IiwiYXJuIjoiYXJuOmF3czppYW06Ojk5NTU1MzQ0MTI2Nzp1c2VyXC9lbGFkdCIsInVzZXJuYW1lIjoiZWxhZHQifQ.zZ-2uunLQVueS5jTb9CpMLw15K4vJKtqxnHU8EFxKdknQsoqinib2CkTtOIJi5OlcPARLELsxdQPdzuNTVeh2PMavr1f2HoI98yFv7P7MuDcPHiP1hjqOH2lKfKdz_ce; AMCV_7742037254C95E840A4C98A6%40AdobeOrg=1585540135%7CMCIDTS%7C19929%7CMCMID%7C00478537719128852711162078240894124399%7CMCAAMLH-1722510386%7C7%7CMCAAMB-1722510386%7C6G1ynYcLPuiQxYZrsz_pkqfLG9yMXBpb2zX5dvJdYQJzPXImdj0y%7CMCOPTOUT-1721912786s%7CNONE%7CMCAID%7CNONE%7CMCSYNCSOP%7C411-19935%7CvVersion%7C4.4.0; aws-mkto-trk=id%3A112-TZM-766%26token%3A_mch-aws.amazon.com-1721119502272-79086; aws_lang=en; session-id=132-8309803-5627536; session-id-time=2082787201l; i18n-prefs=USD; skin=noskin; ubid-main=135-4150462-6528650; csm-hit=tb:QG5H0PA5K446QYHSJB2Q+s-YQB6KZZRS7FQ2YKPDM5Z|1723551626067&t:1723551626067&adb:adblk_no; session-token=LA0VWzaxK3XB/M4tZFaIzVB/IWLx/1DhevSwlj47qcNpS6fDEI+Qj26zf5ZMFFw45Cf8v9osUlJ1B0g/p432vW///rfOwFV+xx6IJ2BqPRRn/E9pEXwGb2OrHgVk9W++g9KUyh8RCaqVGn+bUA8PIT0iqvqhw2CVdLXltCYyyo3TNomUZLfgx5x9HVw7RbGfSauTCLl91WEPJkUuP0yr6fDy90sPSWICng4HRRAUb4EQOuyKJs+b7vvnNy2XgP/sq2/TUxTfJqhZDbhZnyWT099BOw4pwRnxk2D5dESlXr1k2gKa1dqsVqoitg8QhXjsuRLH6jWpwNAZdyv4KNnrxPHhEYRVPbaX; session-token=Zs17uQWB8wOumQNzz+ZM4Wguojbr9jqhAA3ZFxDcxo1PPQNzwlX9TPO84psPYqTl3r/+VCjqAJsYdszwcxuRXfuRJYd1P5+DVoZOQeQ588al6XodljyyPlfhrow3Xj6JYJyHEfuOr/bky1KL4A32q21emX8hS4vU05diiWpYXhEOuwq16i15YP2mr4g1pcQruywEp2M5ZXyFyaEFmGMGVjG9b4egaaBPKuNIwDzdq3yPmctXwtGdgPsVMTFL0qvYucJLmE5l8rDY2Lcl9vOanGB49jtR2r9+G83us3yVeC7UU9gJVipDEoeqEao5+A36oEHScGKbh6ZKyfILjtkiHtj0HB1EHnaN")
                .addHeader("device-memory", "8")
                .addHeader("downlink", "10")
                .addHeader("dpr", "1")
                .addHeader("ect", "4g")
                .addHeader("priority", "u=0, i")
                .addHeader("referer", "https://www.amazon.com/s?k=ipod&crid=1XEMCEY9VOJS5&sprefix=ipod%2Caps%2C121&ref=nb_sb_noss_1")
                .addHeader("rtt", "50")
                .addHeader("sec-ch-device-memory", "8")
                .addHeader("sec-ch-dpr", "1")
                .addHeader("sec-ch-ua", "\"Not/A)Brand\";v=\"8\", \"Chromium\";v=\"126\", \"Google Chrome\";v=\"126\"")
                .addHeader("sec-ch-ua-mobile", "?0")
                .addHeader("sec-ch-ua-platform", "\"Linux\"")
                .addHeader("sec-ch-viewport-width", "885")
                .addHeader("sec-fetch-dest", "document")
                .addHeader("sec-fetch-mode", "navigate")
                .addHeader("sec-fetch-site", "same-origin")
                .addHeader("sec-fetch-user", "?1")
                .addHeader("upgrade-insecure-requests", "1")
                .addHeader("user-agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .addHeader("viewport-width", "885")
                .build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }
    }
