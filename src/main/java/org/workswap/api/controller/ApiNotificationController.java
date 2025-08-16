package org.workswap.api.controller;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workswap.datasource.central.model.Notification;
import org.workswap.datasource.central.model.User;
import org.workswap.common.dto.FullNotificationDTO;
import org.workswap.datasource.central.repository.NotificationRepository;
import org.workswap.core.services.NotificationService;
import org.workswap.core.services.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class ApiNotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping("/for-user/{id}")
    public List<FullNotificationDTO> getNotification(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        User user = userService.findUser(id.toString());

        if(user == userService.findUser(userSub)) {
            return notificationRepository.findByRecipient(user).stream()
                .sorted(Comparator.comparing(Notification::getCreatedAt).reversed())
                .map(notification -> notificationService.toDTO(notification))
                .collect(Collectors.toList());
        } 
        
        return null;
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsReadNotification(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        Notification notification = notificationRepository.findById(id).orElse(null); 

        if (notification.getRecipient() == userService.findUser(userSub)) {
            notification.setRead(true);
            notificationRepository.save(notification);
            return ResponseEntity.ok().build();
        }
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Вы не являетесь получателем этого уведомления");
    }
}
