package org.workswap.api.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.*;
import org.workswap.datasource.central.model.User;
import org.workswap.common.dto.FullNotificationDTO;
import org.workswap.datasource.central.repository.NotificationRepository;
import org.workswap.core.services.NotificationService;
import org.workswap.core.services.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final UserService userService;

    @GetMapping("/for-user/{id}")
    public List<FullNotificationDTO> getNotification(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        User user = userService.findUser(id.toString());

        if (userSub != null) {
            if(user == userService.findUser(userSub)) {
                return notificationRepository.findByRecipient(user).stream()
                    .map(notification -> notificationService.toDTO(notification))
                    .collect(Collectors.toList());
            } 
        }
        
        return null;
    }
}
