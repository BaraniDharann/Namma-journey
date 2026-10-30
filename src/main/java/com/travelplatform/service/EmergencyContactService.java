package com.travelplatform.service;

import com.travelplatform.entity.EmergencyContact;
import com.travelplatform.repository.EmergencyContactRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** A passenger's emergency contacts: who is alerted, with a live link, when they press SOS. */
@Service
public class EmergencyContactService {

    /** Enough for family plus a friend; more turns an SOS into a broadcast. */
    static final int MAX_CONTACTS = 3;

    /** Indian mobile: 10 digits starting 6-9, optionally written with +91 / 0 and spaces or dashes. */
    private static final Pattern INDIAN_MOBILE = Pattern.compile("^(?:\\+?91|0)?[6-9]\\d{9}$");

    private final EmergencyContactRepository repository;
    private final RiderTelegramLinkService linkService;

    public EmergencyContactService(EmergencyContactRepository repository, RiderTelegramLinkService linkService) {
        this.repository = repository;
        this.linkService = linkService;
    }

    @Transactional(readOnly = true)
    public List<ContactView> list(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId).stream().map(ContactView::of).toList();
    }

    /**
     * Adds a contact and, when Telegram is configured, returns their invite link for the
     * passenger to forward - the bot can only message them once they have opened it.
     */
    @Transactional
    public Created add(UUID userId, String name, String phone) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty() || cleanName.length() > 80) {
            throw new IllegalArgumentException("Enter the contact's name (up to 80 characters)");
        }
        String digits = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (!INDIAN_MOBILE.matcher(digits).matches()) {
            throw new IllegalArgumentException("Enter a valid 10-digit mobile number");
        }
        String normalized = digits.substring(digits.length() - 10);
        if (repository.countByUserId(userId) >= MAX_CONTACTS) {
            throw new IllegalArgumentException("You can add up to " + MAX_CONTACTS + " emergency contacts");
        }
        EmergencyContact contact = new EmergencyContact();
        contact.setUserId(userId);
        contact.setName(cleanName);
        contact.setPhone(normalized);
        contact = repository.save(contact);
        String inviteUrl = linkService.available() ? linkService.createContactInviteUrl(contact) : null;
        return new Created(ContactView.of(contact), inviteUrl);
    }

    @Transactional
    public String invite(UUID userId, Long contactId) {
        return linkService.createContactInviteUrl(owned(userId, contactId));
    }

    @Transactional
    public void remove(UUID userId, Long contactId) {
        repository.delete(owned(userId, contactId));
    }

    private EmergencyContact owned(UUID userId, Long contactId) {
        EmergencyContact contact = repository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Contact not found"));
        if (!contact.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not your contact");
        }
        return contact;
    }

    /** @param linked true once they opened the invite, i.e. Telegram alerts can reach them */
    public record ContactView(Long id, String name, String phone, boolean linked) {
        static ContactView of(EmergencyContact c) {
            return new ContactView(c.getId(), c.getName(), c.getPhone(), c.getTelegramChatId() != null);
        }
    }

    /** @param inviteUrl null when Telegram is not configured on the server */
    public record Created(ContactView contact, String inviteUrl) {
    }
}
