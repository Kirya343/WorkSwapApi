package org.workswap.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workswap.datasource.central.model.Listing;
import org.workswap.datasource.central.model.User;
import org.workswap.common.dto.user.UserDTO;
import org.workswap.datasource.central.model.chat.*;
import org.workswap.datasource.central.repository.chat.ChatParticipantRepository;
import org.workswap.datasource.central.repository.chat.ChatRepository;

import jakarta.annotation.security.PermitAll;

import org.workswap.core.services.ChatService;
import org.workswap.core.services.mapping.UserMappingService;
import org.workswap.core.services.query.ListingQueryService;
import org.workswap.core.services.query.UserQueryService;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final ChatRepository chatRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final MessageSource messageSource;
    private final UserQueryService userQueryService;
    private final ListingQueryService listingQueryService;
    private final UserMappingService userMappingService;

    @GetMapping("/get")
    @PermitAll
    public ResponseEntity<?> startNewChat(
        @RequestParam("sellerId") Long sellerId,
        @RequestParam(value = "listingId", required = false) Long listingId,
        @AuthenticationPrincipal User user
    ) {

        User currentUser = userQueryService.findUser(user.getEmail());
        User seller = userQueryService.findUser(sellerId.toString());

        if (seller == null || currentUser == null || currentUser.equals(seller)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Чат не найден"));
        }

        Set<User> participants = Set.of(currentUser, seller);

        Listing listing = null;
        if (listingId != null) {
            listing = listingQueryService.findListing(listingId.toString());
        }

        Chat chat = chatService.getOrCreateChat(participants, listing);

        return ResponseEntity.ok(Map.of("chatId", chat.getId()));
    }

    @GetMapping("/{id}/chat-terms")
    @PreAuthorize("hasAuthority('CHAT_ACCEPT_TERMS')")
    public ResponseEntity<?> getTermsState(
        @PathVariable Long id, 
        @AuthenticationPrincipal User user, 
        Locale locale
    ) {

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
    public ResponseEntity<?> getInterlocutorInfo(@PathVariable Long id, @AuthenticationPrincipal User user) {

        Chat chat = chatRepository.findById(id).orElse(null);
        ChatParticipant participant = chatParticipantRepository.findByUserAndChat(user, chat);
        
        if (!chat.getParticipants().contains(participant)) {
            throw new AccessDeniedException("No access to this chat");
        }

        UserDTO interlocutor = userMappingService.toDto(chat.getInterlocutor(user));

        return ResponseEntity.ok(Map.of("interlocutor", interlocutor));
    }

    @PostMapping("/{id}/accept-terms")
    public ResponseEntity<?> acceptTerms(@PathVariable Long id, @AuthenticationPrincipal User user) {
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
    public ResponseEntity<Void> deleteTemporaryChat(@AuthenticationPrincipal User user) {

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

