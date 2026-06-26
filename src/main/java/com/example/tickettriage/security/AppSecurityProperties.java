package com.example.tickettriage.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code app.security.*}. The API is protected by a shared API key sent
 * in a configurable header.
 */
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    /** Expected API key. Must be overridden in any non-local environment. */
    private String apiKey = "local-dev-key";

    /** Header carrying the API key. */
    private String headerName = "X-API-Key";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }
}
