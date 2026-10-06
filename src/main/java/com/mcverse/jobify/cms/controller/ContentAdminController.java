package com.mcverse.jobify.cms.controller;

import jakarta.validation.Valid;
import com.mcverse.jobify.cms.dto.ContentEntryResponse;
import com.mcverse.jobify.cms.dto.UpdateContentRequest;
import com.mcverse.jobify.cms.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/content")
@Tag(name = "Admin Content", description = "Admin-only CMS endpoints — require ADMIN role")
@SecurityRequirement(name = "bearerAuth")
public class ContentAdminController {

    @Autowired
    private ContentService contentService;

    @GetMapping
    @Operation(summary = "List all content entries with category and last-updated metadata")
    public List<ContentEntryResponse> getAll() {
        return contentService.getAllForAdmin();
    }

    @PutMapping
    @Operation(summary = "Update one or more content entries by key")
    public List<ContentEntryResponse> update(@Valid @RequestBody UpdateContentRequest request) {
        return contentService.updateEntries(request.values());
    }
}
