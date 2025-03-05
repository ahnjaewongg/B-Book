package com.bbook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "slack")
@Getter
@Setter
public class SlackProperties {
    private Webhook webhook = new Webhook();

    @Getter
    @Setter
    public static class Webhook {
        private String url;
    }
}