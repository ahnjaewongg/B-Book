package com.bbook.service;

import com.bbook.entity.Book;
import com.bbook.config.SlackProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackNotificationService {

    private final WebClient slackWebClient;
    private final SlackProperties slackProperties;

    @Async
    public void sendStockAlert(Book book) {
        String message = createStockAlertMessage(book);
        sendMessage(message);
    }

    private String createStockAlertMessage(Book book) {
        return String.format("""
                :rotating_light: *재고 소진 알림* :rotating_light:

                :book: *도서 정보*
                • 도서명: `%s`
                • 작성자: %s
                • 카테고리: %s
                • 최종 재고 소진 시간: %s

                :warning: *빠른 재고 확보가 필요합니다!*
                """,
                book.getTitle(),
                book.getAuthor(),
                book.getDetailCategory(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    public void sendMessage(String message) {
        try {
            String webhookUrl = slackProperties.getWebhook().getUrl();
            if (StringUtils.isEmpty(webhookUrl)) {
                return;
            }

            Map<String, String> slackMessage = Map.of("text", message);

            slackWebClient.post()
                    .bodyValue(slackMessage)
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(response -> log.info("Slack notification sent successfully"))
                    .doOnError(error -> log.error("Failed to send Slack notification: {}", error.getMessage(), error))
                    .subscribe();
        } catch (Exception e) {
            log.error("Failed to send Slack notification: {}", e.getMessage(), e);
        }
    }
}