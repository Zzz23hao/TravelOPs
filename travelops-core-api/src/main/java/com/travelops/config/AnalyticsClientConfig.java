package com.travelops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AnalyticsClientConfig {

    @Bean
    public RestClient analyticsRestClient(RestClient.Builder builder,
                                          @Value("${analytics.base-url}") String baseUrl,
                                          @Value("${analytics.connect-timeout}") Duration connectTimeout,
                                          @Value("${analytics.read-timeout}") Duration readTimeout) {
        return build(builder, baseUrl, connectTimeout, readTimeout);
    }

    /**
     * Without explicit timeouts a hung analytics service would hold a request thread forever.
     * Connect timeout bounds an unreachable host; read timeout bounds a host that accepted the
     * connection but does not answer. Public so the timeout test builds the client the same way.
     */
    public static RestClient build(RestClient.Builder builder, String baseUrl,
                                   Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                // The JDK client otherwise tries an h2c upgrade first, which uvicorn does not support.
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
