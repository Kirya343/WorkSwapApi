package org.workswap.api.controller;

import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.workswap.core.services.ListingService;
import org.workswap.datasource.central.model.Listing;
import org.workswap.datasource.central.model.chat.Chat;
import org.workswap.datasource.central.repository.chat.ChatRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/api/listing")
public class ApiListingsController {
    
    private final ChatRepository chatRepository;
    private final ListingService listingService;

    @GetMapping("/{chatId}/get")
    public String getListing(@PathVariable Long chatId, Locale locale, Model model) {

        Chat conv = chatRepository.findById(chatId).orElse(null);

        Listing listing = null;
        if (conv != null) {
            listing = conv.getListing();
        }

        if (listing == null) {
            return "fragments/empty :: empty";
        }

        listingService.localizeListing(listing, locale);

        model.addAttribute("listing", listing);

        return "fragments/cards/listing-card :: listingCard";
    }
}
