package org.workswap.api.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.workswap.datasource.central.model.Notification;
import org.workswap.datasource.central.model.User;
import org.workswap.common.dto.notification.FullNotificationDTO;
import org.workswap.datasource.central.repository.NotificationRepository;
import org.workswap.core.services.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @GetMapping("/for-user")
    public ResponseEntity<?> getNotification(@AuthenticationPrincipal User user) {
        if(user != null) {
            List<FullNotificationDTO> notifications = notificationRepository.findByRecipient(user).stream()
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .map(notification -> notificationService.toDTO(notification))
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("notifications", notifications));
        } 
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Вы не авторизованы"));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsReadNotification(@PathVariable Long id, @AuthenticationPrincipal User user) {
        Notification notification = notificationRepository.findById(id).orElse(null); 

        if (notification.getRecipient().getId() == user.getId()) {
            notification.setRead(true);
            notificationRepository.save(notification);
            return ResponseEntity.ok().build();
        }
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Вы не являетесь получателем этого уведомления");
    }
}
