package com.bbook.controller;

import com.bbook.service.FirebaseNotificationService;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/fcm")
@RequiredArgsConstructor
public class FCMController {

    private final FirebaseNotificationService firebaseNotificationService;

    @PostMapping("/token")
    public void registerToken(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        try {
            firebaseNotificationService.addAdminToken(token);
        } catch (Exception e) {
            throw e;
        }
    }
}