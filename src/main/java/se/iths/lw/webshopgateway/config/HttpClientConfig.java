package se.iths.lw.webshopgateway.config;



import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

// Creates the HttpClient that the gateway uses to call the other services.
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder() // newBuilder(): start building a configurable HttpClient
                .version(HttpClient.Version.HTTP_1_1) // Force HTTP/1.1 instead of letting the client try HTTP/2 first
                .connectTimeout(Duration.ofSeconds(5)) // Give up if a connection cannot be opened within 5 seconds
                .build();//create the finished, immutable HttpClient.
    }
}