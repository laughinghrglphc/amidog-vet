package com.amidog.app.admin;

import com.amidog.app.auth.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.amidog.app.admin.AdminDtos.AdminClientDetail;
import static com.amidog.app.admin.AdminDtos.AdminClientSummary;
import static com.amidog.app.admin.AdminDtos.AdminClientUpdateRequest;

@RestController
@RequestMapping("/api/v1/admin/clients")
public final class AdminClientController {

    private final AdminQueryService queries;
    private final AdminManagementService management;

    public AdminClientController(
            AdminQueryService queries,
            AdminManagementService management) {
        this.queries = queries;
        this.management = management;
    }

    @GetMapping
    PageResponse<AdminClientSummary> clients(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return queries.clients(
                principal, q, active, page, size);
    }

    @GetMapping("/{id}")
    AdminClientDetail client(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id) {
        return queries.client(principal, id);
    }

    @PatchMapping("/{id}")
    AdminClientSummary update(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id,
            @Valid @RequestBody AdminClientUpdateRequest request) {
        return management.updateClient(principal, id, request);
    }
}
