package com.aiquote.backend.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Kept as its own bean (rather than built inline in GoogleAuthService) so a mock
 * GoogleIdTokenVerifier can be injected in tests. */
@Configuration
public class GoogleAuthConfig {

    @Bean
    GoogleIdTokenVerifier googleIdTokenVerifier(@Value("${app.google.client-id}") String clientId) {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }
}
