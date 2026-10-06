package com.mcverse.jobify.notification.controller;

import com.mcverse.jobify.notification.dto.MarkAllReadResponse;
import com.mcverse.jobify.notification.dto.NotificationResponse;
import com.mcverse.jobify.notification.dto.UnreadCountResponse;
import com.mcverse.jobify.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Notifications", description = "The signed-in user's own notifications — requires Bearer token")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Operation(summary = "List my notifications", description = "Newest first. Any signed-in user, own notifications only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The notifications (may be empty)",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = NotificationResponse.class)))),
            @ApiResponse(responseCode = "400", description = "limit out of range"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
    })
    @GetMapping
    public List<NotificationResponse> list(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Only unread ones") @RequestParam(defaultValue = "false") boolean unreadOnly,
            @Parameter(description = "How many to return, 1 to 100 (default 50)") @RequestParam(required = false)
            Integer limit) {
        return notificationService.list(principal.getUsername(), unreadOnly, limit);
    }

    @Operation(summary = "Count my unread notifications", description = "For the bell badge.")
    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal UserDetails principal) {
        return notificationService.unreadCount(principal.getUsername());
    }

    @Operation(summary = "Mark one notification as read",
            description = "Idempotent. A notification that is not yours is reported as not found.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated notification",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "404", description = "Not found, or not yours"),
    })
    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal UserDetails principal,
                                         @Parameter(description = "Notification id (UUID)") @PathVariable String id) {
        return notificationService.markRead(id, principal.getUsername());
    }

    @Operation(summary = "Mark all my notifications as read")
    @PostMapping("/read-all")
    public MarkAllReadResponse markAllRead(@AuthenticationPrincipal UserDetails principal) {
        return notificationService.markAllRead(principal.getUsername());
    }
}
