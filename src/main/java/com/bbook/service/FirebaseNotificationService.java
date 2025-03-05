package com.bbook.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@Service
public class FirebaseNotificationService {

    private List<String> adminTokens = new ArrayList<>();

    public void addAdminToken(String token) {
        if (!adminTokens.contains(token)) {
            adminTokens.add(token);
        }
    }

    public void sendSubscriptionNotification(String memberName, String subscriptionType) {
        try {
            for (String token : adminTokens) {
                Message tokenMessage = Message.builder()
                        .setNotification(Notification.builder()
                                .setTitle("새로운 구독 알림")
                                .setBody(memberName + "님이 " + subscriptionType + " 구독을 시작했습니다.")
                                .build())
                        .putData("type", "subscription")
                        .putData("memberName", memberName)
                        .putData("subscriptionType", subscriptionType)
                        .setToken(token)
                        .build();

                FirebaseMessaging.getInstance().send(tokenMessage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}