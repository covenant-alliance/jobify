package com.mcverse.jobify.admin;

import com.mcverse.jobify.admin.dto.AdminStatsResponse;
import com.mcverse.jobify.admin.service.AdminStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Admin-only endpoints — require ADMIN role")
@SecurityRequirement(name = "bearerAuth")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    public AdminStatsController(AdminStatsService adminStatsService) {
        this.adminStatsService = adminStatsService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Platform headline numbers",
            description = "ADMIN only. Users by role, jobs (open and closed), applications by status, recent " +
                    "activity, and pending deletion requests.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The numbers"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Not an admin"),
    })
    public AdminStatsResponse stats(@AuthenticationPrincipal UserDetails principal) {
        return adminStatsService.stats(principal.getUsername());
    }
}
