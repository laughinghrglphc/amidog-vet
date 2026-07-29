package com.amidog.app.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ContactDtos {

    private ContactDtos() {
    }

    public record ContactRequest(
            @NotBlank @Size(min = 2, max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 2000) String message,
            @Size(max = 200) String website
    ) {
    }

    public record ContactResponse(String message) {
    }
}
