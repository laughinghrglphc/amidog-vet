package com.amidog.app.catalog;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

import static com.amidog.app.catalog.ServiceDtos.ServiceCreateRequest;
import static com.amidog.app.catalog.ServiceDtos.ServiceResponse;
import static com.amidog.app.catalog.ServiceDtos.ServiceUpdateRequest;

@RestController
@RequestMapping("/api/v1")
public class ServiceCatalogController {

    private final ServiceCatalogService catalog;

    ServiceCatalogController(ServiceCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/services")
    List<ServiceResponse> publicCatalog() {
        return catalog.publicCatalog();
    }

    @GetMapping("/admin/services")
    List<ServiceResponse> adminCatalog() {
        return catalog.adminCatalog();
    }

    @PostMapping("/admin/services")
    ResponseEntity<ServiceResponse> create(@Valid @RequestBody ServiceCreateRequest request) {
        ServiceResponse response = catalog.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/services/" + response.id())).body(response);
    }

    @PatchMapping("/admin/services/{id}")
    ServiceResponse update(@PathVariable long id, @Valid @RequestBody ServiceUpdateRequest request) {
        return catalog.update(id, request);
    }

    @DeleteMapping("/admin/services/{id}")
    ResponseEntity<Void> archive(@PathVariable long id) {
        catalog.archive(id);
        return ResponseEntity.noContent().build();
    }
}
