package org.workswap.api.controller;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.workswap.datasource.central.model.User;
import org.workswap.common.dto.*;
import org.workswap.common.dto.ChatRequest;
import org.workswap.datasource.central.model.chat.*;
import org.workswap.core.services.NotificationService;
import org.workswap.core.services.UserService;
import org.workswap.core.services.ChatService;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@PreAuthorize("hasAuthority('CHAT_USE')")
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry simpUserRegistry;
    private final ChatService chatService;
    private final UserService userService;
    private final NotificationService notificationService;

    @PreAuthorize("hasAuthority('CHAT_SEND_MESSAGES')")
    @MessageMapping("/chat.send")
    public void sendMessage(MessageDTO messageDTO, Principal principal, @Header("locale") String lang) throws AccessDeniedException {
        Locale locale = Locale.of(lang);

        User sender = userService.findUser(principal.getName());
        Chat chat = chatService.getChatById(messageDTO.getChatId());
        Message message = chatService.sendMessage(chat, sender, messageDTO.getText());

        messagingTemplate.convertAndSend("/topic/messages/" + messageDTO.getChatId(),
                new MessageDTO(
                        message.getId(),
                        message.getText(),
                        message.getSentAt(),
                        message.getSender().getId(),
                        message.getReceiver().getId(),
                        message.getChat().getId(),
                        message.isOwn(sender)
                )
        );

        // Установка диалога постоянным
        if (chat.isTemporary()) {
            chatService.setPermanentChat(chat);
        }

        // Отправка уведомления получателю
        User receiver = message.getReceiver();

        NotificationDTO notification = new NotificationDTO(
                "Новое сообщение",
                sender.getName() + ": " + message.getText(),
                "/secure/messengerchatId=" + chat.getId()
        );

        chatService.notifyChatUpdate(message.getChat().getId(), sender, locale);
        chatService.notifyChatUpdate(message.getChat().getId(), receiver, locale);

        if (isUserOnline(receiver)) {
            messagingTemplate.convertAndSendToUser(
                    receiver.getSub(),
                    "/queue/notifications",
                    notification
            );
        } else {
            notificationService.saveOfflineChatNotification(receiver.getSub(), notification);
        }
    }

    // Проверка активности пользователя
    private boolean isUserOnline(User user) {
        return simpUserRegistry.getUser(user.getSub()) != null;
    }

    @PreAuthorize("hasAuthority('CHAT_GET_MESSAGES')")
    @MessageMapping("/chat.loadMessages/{chatId}")
    @SendTo("/topic/history.messages/{chatId}")
    public List<MessageDTO> loadMessagesForChat(@DestinationVariable Long chatId, Principal principal) {
        logger.info("Получение сообщений для разговора с ID: {}", chatId);

        User currentUser = userService.findUser(principal.getName());

        // Получаем разговор по ID
        Chat chat = chatService.getChatById(chatId);
        if (chat == null) {
            throw new AccessDeniedException("Chat not found");
        }

        // Получаем все сообщения для этого разговора
        List<Message> messages = chatService.getMessages(chat);

        // Преобразуем сообщения в DTO и отправляем клиенту
        return messages.stream()
                        .map(msg -> new MessageDTO(
                                msg.getId(),
                                msg.getText(),
                                msg.getSentAt(),
                                msg.getSender().getId(),
                                chat.getId(),
                                msg.getReceiver().getId(),
                                msg.getSender().equals(currentUser) // Проверка на владельца сообщения
                        ))
                        .collect(Collectors.toList());
    }

    @MessageMapping("/chat.markAsRead")
    public void markAsRead(MarkAsReadDTO markAsReadDTO, Principal principal, @Header("locale") String lang) {
        Locale locale = Locale.of(lang);
        User user = userService.findUser(principal.getName());
        Long chatId = markAsReadDTO.getChatId();

        chatService.markMessagesAsRead(chatId, user);
        // Уведомляем об обновлении
        chatService.notifyChatUpdate(markAsReadDTO.getChatId(), user, locale);
    }

    @PreAuthorize("hasAuthority('CHAT_GET_CHATS')")
    @MessageMapping("/getChats")
    public void getChats(Principal principal, @Header("locale") String lang) {
        logger.info("Начата функция получения диалогов");
        Locale locale = Locale.of(lang);
        User user = userService.findUser(principal.getName());
        List<ChatDTO> chats = chatService.getChatsDTOForUser(user, locale);
        logger.info("Получили разговоры в виде DTO");
        chats.stream()
            .forEach(dto -> {
                messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/chats",
                    dto
                );
                logger.info("Отправили диалог: " + dto.getInterlocutorName());
            });
    }

    @PreAuthorize("hasAuthority('CHAT_GET_INTERLOCUTOR')")
    @Transactional
    @MessageMapping("/chat.getInterlocutorInfo")
    @SendToUser("/queue/interlocutorInfo")
    public InterlocutorInfoDTO getInterlocutorInfo(ChatRequest request, Principal principal) {
        User currentUser = userService.findUser(principal.getName());
        Long chatId = request.getChatId();

        Chat chat = chatService.getChatById(chatId);
        if (chat == null) {
            throw new AccessDeniedException("No access to this chat");
        }

        User interlocutor = chat.getInterlocutor(currentUser);
        if (interlocutor == null) {
            throw new AccessDeniedException("No access to this chat");
        }

        return new InterlocutorInfoDTO(interlocutor.getName(), interlocutor.getAvatarUrl());
    }
}