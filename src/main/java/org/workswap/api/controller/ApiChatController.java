package org.workswap.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.datasource.central.model.User;
import org.workswap.common.dto.InterlocutorInfoDTO;
import org.workswap.common.dto.MessageDTO;
import org.workswap.datasource.central.model.chat.*;
import org.workswap.datasource.central.repository.chat.ChatParticipantRepository;
import org.workswap.datasource.central.repository.chat.ChatRepository;
import org.workswap.core.services.UserService;
import org.workswap.core.services.ChatService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ApiChatController {

    private static final Logger logger = LoggerFactory.getLogger(ApiChatController.class);

    private final ChatService chatService;
    private final UserService userService;
    private final ChatRepository chatRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final MessageSource messageSource;


    @GetMapping("/{chatId}/messages")
    public ResponseEntity<List<MessageDTO>> getChatMessages(@PathVariable Long chatId, @RequestHeader("X-User-Sub") String userSub) {
        User currentUser = userService.findUser(userSub);

        Chat chat = chatService.getChatById(chatId);

        if (chat == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();  // 404, если беседа не найдена
        }

        // Маркируем сообщения как прочитанные
        chatService.markMessagesAsRead(chatId, currentUser);

        // Получаем сообщения
        List<Message> messages = chatService.getMessages(chat);

        if (messages.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();  // 204, если сообщений нет
        }

        // Преобразуем в DTO и возвращаем
        List<MessageDTO> messageDTOs = messages.parallelStream()
                .map(msg -> new MessageDTO(
                        msg.getId(),
                        msg.getText(),
                        msg.getSentAt(),
                        msg.getSender().getId(),
                        msg.getReceiver().getId(),
                        msg.getChat().getId(),
                        msg.isOwn(currentUser)
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(messageDTOs);
    }

    @GetMapping("/{id}/chat-terms")
    public ResponseEntity<?> getTermsState(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub, Locale locale) {
        User user = userService.findUser(userSub);
        Chat chat = chatRepository.findById(id).orElse(null);

        ChatParticipant participant = chatParticipantRepository.findByUserAndChat(user, chat);
        if (!chat.getParticipants().contains(participant)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }


        return ResponseEntity.ok(Map.of("chatTermsAccepted", participant.isChatTermsAccepted(), 
                                        "message", messageSource.getMessage("chat.terms.message", null, locale), 
                                        "messageAccept", messageSource.getMessage("security.confirm", null, locale)));
    }

    @PreAuthorize("hasAuthority('CHAT_GET_INTERLOCUTOR')")
    @GetMapping("/{id}/getInterlocutorInfo")
    public InterlocutorInfoDTO getInterlocutorInfo(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        User user = userService.findUser(userSub);
        Chat chat = chatRepository.findById(id).orElse(null);
        ChatParticipant participant = chatParticipantRepository.findByUserAndChat(user, chat);
        
        if (!chat.getParticipants().contains(participant)) {
            throw new AccessDeniedException("No access to this chat");
        }

        User interlocutor = chat.getInterlocutor(user);

        return new InterlocutorInfoDTO(interlocutor.getName(), interlocutor.getAvatarUrl());
    }

    @PostMapping("/{id}/accept-terms")
    public ResponseEntity<?> acceptTerms(@PathVariable Long id, @RequestHeader("X-User-Sub") String userSub) {
        User user = userService.findUser(userSub);
        Chat chat = chatRepository.findById(id).orElse(null);
        ChatParticipant participant = chatParticipantRepository.findByUserAndChat(user, chat);
        
        if (!chat.getParticipants().contains(participant)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        participant.setChatTermsAccepted(true);
        chatParticipantRepository.save(participant);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/temporary")
    public ResponseEntity<Void> deleteTemporaryChat(@RequestHeader("X-User-Sub") String userSub) {

        User user = userService.findUser(userSub);
        logger.debug("Запрос на удаление временных диалогов от пользователя: {}", user.getName());

        List<Chat> chats = chatRepository.findAllByParticipant(user);

        logger.debug("Найдено диалогов у пользователя: {}", chats.size());

        int removedCount = 0;
        for (Chat chat : chats) {
            if (chat.getMessages().isEmpty()) {
                logger.debug("Найден пустой диалог с ID: {}", chat.getId());
                if (chat.isTemporary()) {
                    logger.debug("Удаляется временный диалог с ID: {}", chat.getId());
                    chatRepository.delete(chat);
                    removedCount++;
                }
            }
        }

        logger.debug("Удалено {} временных диалогов для пользователя {}", removedCount, user.getName());
        return ResponseEntity.ok().build();
    }
}

