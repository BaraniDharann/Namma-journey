package com.travelplatform.service;

import com.travelplatform.config.SafetyProperties;
import com.travelplatform.entity.Driver;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static com.travelplatform.service.TelegramClient.escapeHtml;

/**
 * Messages to the owner's Telegram group (the same group as SOS alerts, {@code SAFETY_OPS_CHAT_ID})
 * that need a decision: a new driver application, a driver's commission payment to confirm.
 *
 * <p>Buttons pressed there come back through the webhook and are honoured only when the press
 * happened in this group (see {@link #isOwnerChat}): everyone in it acts for the owner, so who
 * is added to the group is the owner's access control.
 */
@Component
public class OwnerTelegramAlerts {

    public static final String APPROVE = "drv_ok:";
    public static final String REJECT = "drv_no:";
    public static final String HOLD = "drv_hold:";
    public static final String DUES_CONFIRM = "due_ok:";
    public static final String DUES_REJECT = "due_no:";

    private final TelegramClient telegram;
    private final SafetyProperties props;
    private final AfterCommitDispatcher dispatcher;

    public OwnerTelegramAlerts(TelegramClient telegram, SafetyProperties props, AfterCommitDispatcher dispatcher) {
        this.telegram = telegram;
        this.props = props;
        this.dispatcher = dispatcher;
    }

    public static boolean isOwnerAction(String data) {
        return data != null && (data.startsWith(APPROVE) || data.startsWith(REJECT) || data.startsWith(HOLD)
                || data.startsWith(DUES_CONFIRM) || data.startsWith(DUES_REJECT));
    }

    /** True when a button press came from the owner's group, the only place owner buttons are honoured. */
    public boolean isOwnerChat(String chatId) {
        return props.hasOpsChat() && props.getOpsChatId().equals(chatId);
    }

    /**
     * A driver application reached the owner: first time (with the automatic-approval deadline,
     * if any) or a re-upload after a rejection.
     */
    public void driverApplication(Driver d, boolean resubmitted) {
        StringBuilder text = new StringBuilder(resubmitted
                ? "📄 <b>Driver re-uploaded documents</b>\n\n"
                : "🆕 <b>New driver application</b>\n\n");
        text.append(driverLines(d));
        List<List<TelegramClient.InlineButton>> buttons;
        if (d.getAutoApproveAt() != null) {
            long minutes = Math.max(1, Duration.between(LocalDateTime.now(), d.getAutoApproveAt()).toMinutes());
            text.append("\n⏱ <b>Approved automatically in about ").append(minutes)
                    .append(" min</b> if the checks pass, unless you Reject or Hold.\n");
            buttons = List.of(List.of(
                    new TelegramClient.InlineButton("✅ Approve now", APPROVE + d.getId()),
                    new TelegramClient.InlineButton("❌ Reject", REJECT + d.getId()),
                    new TelegramClient.InlineButton("⏸ Hold", HOLD + d.getId())));
        } else {
            text.append("\n👀 <b>Needs your decision</b> - this one is not approved automatically.\n");
            buttons = decisionButtons(d.getId());
        }
        text.append("\nDocuments: ").append(props.getPublicBaseUrl()).append("/owner/driver-applications");
        send(text.toString(), buttons, null);
    }

    public static List<List<TelegramClient.InlineButton>> decisionButtons(Long driverId) {
        return List.of(List.of(
                new TelegramClient.InlineButton("✅ Approve", APPROVE + driverId),
                new TelegramClient.InlineButton("❌ Reject", REJECT + driverId)));
    }

    static String driverLines(Driver d) {
        StringBuilder s = new StringBuilder();
        s.append("<b>Name:</b> ").append(escapeHtml(d.getName())).append('\n');
        s.append("<b>Mobile:</b> ").append(escapeHtml(d.getMobile())).append('\n');
        String vehicle = ((d.getVehicleModel() == null ? "" : d.getVehicleModel()) + " "
                + (d.getVehicleNumber() == null ? "" : d.getVehicleNumber())).trim();
        if (!vehicle.isEmpty()) {
            s.append("<b>Vehicle:</b> ").append(escapeHtml(vehicle))
                    .append(d.getVehicleType() == null ? "" : " (" + d.getVehicleType().name() + ")").append('\n');
        }
        s.append("<b>Licence:</b> ").append(escapeHtml(d.getLicenseNumber())).append('\n');
        return s.toString();
    }

    /**
     * Sends to the owner group after commit. {@code onSent} receives the message id (to rewrite the
     * message once decided); it is not called when the group is not configured or the send fails.
     */
    public void send(String html, List<List<TelegramClient.InlineButton>> buttons, Consumer<Long> onSent) {
        if (!props.hasOpsChat()) {
            return;
        }
        String chat = props.getOpsChatId();
        dispatcher.dispatch("telegram", () -> {
            Long id = telegram.sendMessageForId(chat, html, buttons);
            if (id != null && onSent != null) {
                onSent.accept(id);
            }
        });
    }

    /**
     * What an owner button press did.
     *
     * @param toast    the short pop-up on the pressed button
     * @param appended HTML line added under the pressed message, or null when the message was
     *                 already rewritten (or should stay as it is)
     * @param buttons  the buttons the message keeps (Hold leaves Approve / Reject); empty drops them
     */
    public record Outcome(String toast, String appended, List<List<TelegramClient.InlineButton>> buttons) {
    }

    /** Rewrites a decided message in the group and drops its buttons. */
    public void rewrite(Long messageId, String html) {
        if (!props.hasOpsChat() || messageId == null) {
            return;
        }
        String chat = props.getOpsChatId();
        dispatcher.dispatch("telegram", () -> telegram.editMessageText(chat, messageId, html));
    }
}
