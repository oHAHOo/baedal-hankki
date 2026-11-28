package com.example.baedalhankki.refund.controller;

import com.example.baedalhankki.refund.service.TossWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TossWebhookController {
    private final TossWebhookService service;

    @PostMapping("/api/payments/webhooks/toss")
    public ResponseEntity<Void> receive(@RequestBody byte[] raw,
            @RequestHeader("tosspayments-webhook-transmission-time") String timestamp,
            @RequestHeader("tosspayments-webhook-signature") String signature) {
        service.receive(raw, timestamp, signature);
        return ResponseEntity.ok().build();
    }
}
