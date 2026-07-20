package com.amidog.app.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ServiceDtos {

    private ServiceDtos() {
    }

    public record ServiceResponse(
            Long id, String code, String name, String description, boolean active, int displayOrder) {
        static ServiceResponse from(ServiceOffering service) {
            return new ServiceResponse(
                    service.getId(), service.getCode(), service.getName(), service.getDescription(),
                    service.isActive(), service.getDisplayOrder());
        }
    }

    public record ServiceCreateRequest(
            @NotBlank @Size(max = 60) String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Min(0) int displayOrder) {
    }

    public record ServiceUpdateRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String description,
            @Min(0) int displayOrder,
            boolean active) {
    }
}
