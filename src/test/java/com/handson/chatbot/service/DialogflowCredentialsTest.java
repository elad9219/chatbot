package com.handson.chatbot.service;

import com.google.api.gax.core.GoogleCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DialogflowCredentialsTest {

    @Test
    void loadsServiceAccountFromRuntimeJsonWithoutContactingGoogle() throws Exception {
        var settings = DialogflowService.clientSettings(
                credentialJson("elad-chatbot-dsci", "https://oauth2.googleapis.com/token"));
        var credentials = settings.getCredentialsProvider().getCredentials();

        assertThat(credentials).isInstanceOf(ServiceAccountCredentials.class);
        assertThat(((ServiceAccountCredentials) credentials).getProjectId())
                .isEqualTo("elad-chatbot-dsci");
        assertThat(((ServiceAccountCredentials) credentials).getScopes())
                .contains("https://www.googleapis.com/auth/cloud-platform");
    }

    @Test
    void preservesDefaultCredentialLoadingWhenRuntimeJsonIsAbsent() throws Exception {
        assertThat(DialogflowService.clientSettings("").getCredentialsProvider())
                .isInstanceOf(GoogleCredentialsProvider.class);
    }

    @Test
    void rejectsMalformedJsonWithoutIncludingCredentialDataInTheError() {
        assertThatThrownBy(() -> DialogflowService.clientSettings("private-invalid-value"))
                .isInstanceOf(IOException.class)
                .hasMessage("Invalid Dialogflow credential configuration")
                .hasNoCause();
    }

    @Test
    void rejectsAnotherProjectAndAnUnexpectedTokenEndpoint() throws Exception {
        for (String json : new String[]{
                credentialJson("another-project", "https://oauth2.googleapis.com/token"),
                credentialJson("elad-chatbot-dsci", "https://untrusted.example/token")
        }) {
            assertThatThrownBy(() -> DialogflowService.clientSettings(json))
                    .isInstanceOf(IOException.class)
                    .hasMessage("Invalid Dialogflow credential configuration");
        }
    }

    private String credentialJson(String project, String tokenUri) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String key = Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        return JsonMapper.builder().build().writeValueAsString(Map.of(
                "type", "service_account",
                "project_id", project,
                "private_key_id", "test-key",
                "private_key", "-----BEGIN PRIVATE KEY-----\n" + key + "\n-----END PRIVATE KEY-----\n",
                "client_email", "test@" + project + ".iam.gserviceaccount.com",
                "client_id", "1234567890",
                "token_uri", tokenUri));
    }
}