package com.bbook.service;

import com.bbook.entity.Member;
import com.bbook.entity.Subscription;
import com.bbook.entity.Subscription.SubscriptionType;
import com.bbook.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.time.LocalDateTime;
import java.util.Optional;
    
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final NotificationService notificationService;
    private final RestTemplate restTemplate;

    @Value("${imp.api.key}")
    private String IMP_KEY;

    @Value("${imp.api.secret}")
    private String IMP_SECRET;

    @Transactional
    public void createSubscription(Member member, SubscriptionType type, String merchantUid, String impUid,
            String customerUid) {

        Optional<Subscription> existingSubscription = subscriptionRepository.findByMemberId(member.getId());
        if (existingSubscription.isPresent() && existingSubscription.get().isActive()) {
            throw new RuntimeException("이미 활성화된 구독이 존재합니다.");
        }

        // 빌링키 발급 확인
        try {
            String accessToken = getIamportToken();

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> billingResponse = restTemplate.exchange(
                    "https://api.iamport.kr/subscribe/customers/" + customerUid,
                    HttpMethod.GET,
                    entity,
                    Map.class);

            if (billingResponse.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("빌링키 조회 실패");
            }

            // 구독 생성
            Subscription subscription = Subscription.createSubscription(member, type);
            subscription.setMerchantUid(merchantUid);
            subscription.setImpUid(impUid);
            subscription.setCustomerUid(customerUid);

            subscriptionRepository.save(subscription);

            String message = String.format("[새로운 구독] %s님이 %s 구독을 시작했습니다.",
                    member.getNickname(),
                    type == SubscriptionType.MONTHLY ? "월간" : "연간");
            notificationService.sendToAdmin(message);

        } catch (Exception e) {
            log.error("구독 생성 중 오류 발생", e);
            throw new RuntimeException("구독 생성 실패: " + e.getMessage());
        }
    }

    private String getIamportToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("imp_key", IMP_KEY);
        formData.add("imp_secret", IMP_SECRET);

        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> tokenEntity = new HttpEntity<>(formData, tokenHeaders);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                "https://api.iamport.kr/users/getToken",
                HttpMethod.POST,
                tokenEntity,
                Map.class);

        if (tokenResponse.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("토큰 발급 실패");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> responseData = (Map<String, Object>) tokenResponse.getBody().get("response");
        return (String) responseData.get("access_token");
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void processRecurringPayments() {

        List<Subscription> activeSubscriptions = subscriptionRepository.findByIsActiveTrue();

        for (Subscription subscription : activeSubscriptions) {
            try {

                // 결제 주기 확인
                LocalDateTime nextPaymentDate = subscription.getNextPaymentDate();
                LocalDateTime now = LocalDateTime.now();
                if (now.isAfter(nextPaymentDate)) {

                    processPayment(subscription);

                    // 결제 성공 시 다음 결제일 업데이트
                    LocalDateTime newNextPaymentDate = subscription.getType() == SubscriptionType.MONTHLY
                            ? now.plusMinutes(1)
                            : now.plusMinutes(1);

                    subscription.setNextPaymentDate(newNextPaymentDate);
                    subscriptionRepository.save(subscription);
                }

            } catch (Exception e) {
                notificationService.sendToAdmin(String.format("[정기결제 실패] %s님의 구독 갱신에 실패했습니다: %s",
                        subscription.getMember().getNickname(), e.getMessage()));
            }
        }
    }

    // 실제 결제 처리 메서드 분리
    private void processPayment(Subscription subscription) {
        String customerUid = subscription.getCustomerUid();
        int amount = subscription.getPrice();

        try {
            String token = getIamportToken();

            // 결제 요청 데이터
            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("customer_uid", customerUid);
            paymentData.put("merchant_uid", "subscription_" + System.currentTimeMillis());
            paymentData.put("amount", amount);
            paymentData.put("name", "BBOOK " +
                    (subscription.getType() == SubscriptionType.MONTHLY ? "월간" : "연간") + " 구독");
            paymentData.put("pg", "tosspayments");

            // HTTP 요청 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paymentData, headers);

            // 결제 요청
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://api.iamport.kr/subscribe/payments/again",
                    HttpMethod.POST,
                    entity,
                    Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                if (responseBody != null && responseBody.get("code").equals(0)) {
                    notificationService.sendToAdmin(String.format("[정기결제 성공] %s님의 구독이 갱신되었습니다.",
                            subscription.getMember().getNickname()));
                } else {
                    throw new RuntimeException(
                            "결제 실패: " + (responseBody != null ? responseBody.get("message") : "응답이 없습니다."));
                }
            } else {
                throw new RuntimeException("결제 요청 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("정기결제 처리 중 오류 발생", e);
            throw new RuntimeException("정기결제 처리 실패: " + e.getMessage());
        }
    }
}