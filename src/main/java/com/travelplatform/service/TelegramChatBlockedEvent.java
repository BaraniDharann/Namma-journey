package com.travelplatform.service;

/**
 * Telegram refused to deliver to this chat (403 Forbidden): the person blocked the bot or
 * deleted their account. Published by {@link TelegramClient}, which knows the HTTP outcome but
 * not which driver owns the chat; {@link TelegramLinkService} listens and unlinks that driver,
 * so the driver app locks until they reconnect.
 */
public record TelegramChatBlockedEvent(String chatId) {
}
