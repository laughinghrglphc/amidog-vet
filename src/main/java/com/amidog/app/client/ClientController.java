package com.amidog.app.client;

import com.amidog.app.auth.AccountPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

import static com.amidog.app.client.ClientDtos.ClientProfileResponse;
import static com.amidog.app.client.ClientDtos.ClientProfileUpdateRequest;
import static com.amidog.app.client.ClientDtos.PetResponse;
import static com.amidog.app.client.ClientDtos.PetWriteRequest;

@RestController
@RequestMapping("/api/v1/me")
public class ClientController {

    private final ClientProfileService profiles;
    private final PetService pets;

    public ClientController(ClientProfileService profiles, PetService pets) {
        this.profiles = profiles;
        this.pets = pets;
    }

    @GetMapping("/profile")
    ClientProfileResponse profile(@AuthenticationPrincipal AccountPrincipal principal) {
        return profiles.get(principal);
    }

    @PatchMapping("/profile")
    ClientProfileResponse updateProfile(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody ClientProfileUpdateRequest request) {
        return profiles.update(principal, request);
    }

    @GetMapping("/pets")
    List<PetResponse> pets(@AuthenticationPrincipal AccountPrincipal principal) {
        return pets.list(principal);
    }

    @PostMapping("/pets")
    ResponseEntity<PetResponse> createPet(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody PetWriteRequest request) {
        PetResponse response = pets.create(principal, request);
        return ResponseEntity.created(URI.create("/api/v1/me/pets/" + response.id())).body(response);
    }

    @PatchMapping("/pets/{id}")
    PetResponse updatePet(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id,
            @Valid @RequestBody PetWriteRequest request) {
        return pets.update(principal, id, request);
    }

    @DeleteMapping("/pets/{id}")
    ResponseEntity<Void> archivePet(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable long id) {
        pets.archive(principal, id);
        return ResponseEntity.noContent().build();
    }
}
