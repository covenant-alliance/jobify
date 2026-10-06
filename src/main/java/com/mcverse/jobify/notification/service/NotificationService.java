package com.mcverse.jobify.notification.service;

import com.mcverse.jobify.common.exception.ResourceNotFoundException;
import com.mcverse.jobify.notification.dto.MarkAllReadResponse;
import com.mcverse.jobify.notification.dto.NotificationResponse;
import com.mcverse.jobify.notification.dto.UnreadCountResponse;
import com.mcverse.jobify.notification.model.Notification;
import com.mcverse.jobify.notification.model.NotificationType;
import com.mcverse.jobify.notification.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a notification. It joins the caller's transaction, so a message is never sent for a change that was
     * rolled back, and a failure to store it rolls the change back too.
     */
    @Transactional
    public void notify(String recipientUsername, NotificationType type, String title, String body,
                       Integer jobId, String applicationId) {
        repository.save(new Notification(recipientUsername, type, title, truncate(body), jobId, applicationId));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String username, boolean unreadOnly, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : limit;
        if (size < 1 || size > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT + ".");
        }
        PageRequest page = PageRequest.of(0, size);
        List<Notification> found = unreadOnly
                ? repository.findByRecipientUsernameAndIsReadFalseOrderByCreatedAtDesc(username, page)
                : repository.findByRecipientUsernameOrderByCreatedAtDesc(username, page);
        return found.stream().map(NotificationService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String username) {
        return new UnreadCountResponse(repository.countByRecipientUsernameAndIsReadFalse(username));
    }

    /** Idempotent. Someone else's notification is reported as not found, so ids cannot be probed. */
    @Transactional
    public NotificationResponse markRead(String id, String username) {
        Notification notification = repository.findByIdAndRecipientUsername(id, username)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        notification.markRead();
        return toResponse(repository.save(notification));
    }

    @Transactional
    public MarkAllReadResponse markAllRead(String username) {
        return new MarkAllReadResponse(repository.markAllRead(username));
    }

    @Transactional
    public void deleteAllFor(String username) {
        repository.deleteByRecipientUsername(username);
    }

    private static String truncate(String body) {
        return body.length() <= 1000 ? body : body.substring(0, 997) + "...";
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.isRead(),
                n.getCreatedAt(), n.getJobId(), n.getApplicationId());
    }
}
