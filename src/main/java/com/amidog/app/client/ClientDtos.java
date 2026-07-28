package com.amidog.app.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class ClientDtos {

    private ClientDtos() {
    }

    public record ClientProfileResponse(Long id, String name, String phone, String email) {
    }

    public record ClientProfileUpdateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 30) String phone) {
    }

    public record PetResponse(
            Long id, String name, String species, String breed, LocalDate birthdate, boolean active) {
        static PetResponse from(Pet pet) {
            return new PetResponse(
                    pet.getId(), pet.getName(), pet.getSpecies(), pet.getBreed(),
                    pet.getBirthdate(), pet.isActive());
        }
    }

    public record PetWriteRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 40) String species,
            @Size(max = 80) String breed,
            @PastOrPresent LocalDate birthdate) {
    }
}
