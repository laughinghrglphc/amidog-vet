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

import static com.amidog.app.admin.AdminDtos.AdminPetDetail;
import static com.amidog.app.admin.AdminDtos.AdminPetSummary;
import static com.amidog.app.admin.AdminDtos.AdminPetUpdateRequest;

@RestController
@RequestMapping("/api/v1/admin/pets")
public final class AdminPetController {

    private final AdminQueryService queries;
    private final AdminManagementService management;

    public AdminPetController(
            AdminQueryService queries,
            AdminManagementService management) {
        this.queries = queries;
        this.management = management;
    }

    @GetMapping
    PageResponse<AdminPetSummary> pets(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Long clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return queries.pets(
                principal, q, active, clientId, page, size);
    }

    @GetMapping("/{id}")
    AdminPetDetail pet(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id) {
        return queries.pet(principal, id);
    }

    @PatchMapping("/{id}")
    AdminPetSummary update(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id,
            @Valid @RequestBody AdminPetUpdateRequest request) {
        return management.updatePet(principal, id, request);
    }
}
