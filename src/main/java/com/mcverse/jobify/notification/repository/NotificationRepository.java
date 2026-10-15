package com.mcverse.jobify.notification.repository;

import com.mcverse.jobify.notification.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByRecipientUsernameOrderByCreatedAtDesc(String recipientUsername, Pageable pageable);

    List<Notification> findByRecipientUsernameAndIsReadFalseOrderByCreatedAtDesc(String recipientUsername,
                                                                                  Pageable pageable);

    Optional<Notification> findByIdAndRecipientUsername(String id, String recipientUsername);

    long countByRecipientUsernameAndIsReadFalse(String recipientUsername);

    @Modifying
    @Query("update Notification n set n.isRead = true where n.recipientUsername = :username and n.isRead = false")
    int markAllRead(String username);

    /** Used when an account is deleted. */
    @Modifying
    @Query("delete from Notification n where n.recipientUsername = :username")
    int deleteByRecipientUsername(String username);

    /** Read notifications older than the cut-off: the bell never shows them again. */
    @Modifying
    @Query("delete from Notification n where n.isRead = true and n.createdAt < :before")
    int deleteReadBefore(java.time.LocalDateTime before);

    /** Anything, read or not, older than the cut-off. */
    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteBefore(java.time.LocalDateTime before);
}
