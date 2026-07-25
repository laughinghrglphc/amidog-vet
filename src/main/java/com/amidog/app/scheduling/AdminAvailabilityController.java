package com.amidog.app.scheduling;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

import static com.amidog.app.scheduling.AvailabilityDtos.AvailabilityBlockRequest;
import static com.amidog.app.scheduling.AvailabilityDtos.AvailabilityBlockResponse;
import static com.amidog.app.scheduling.AvailabilityDtos.WeeklyIntervalRequest;
import static com.amidog.app.scheduling.AvailabilityDtos.WeeklyIntervalResponse;

@RestController
@RequestMapping("/api/v1/admin/availability")
public class AdminAvailabilityController {

    private final AvailabilityAdministrationService administration;

    public AdminAvailabilityController(
            AvailabilityAdministrationService administration) {
        this.administration = administration;
    }

    @GetMapping("/weekly")
    List<WeeklyIntervalResponse> weekly() {
        return administration.weeklySchedule();
    }

    @PutMapping("/weekly")
    List<WeeklyIntervalResponse> replaceWeekly(
            @Valid @RequestBody List<@Valid WeeklyIntervalRequest> intervals) {
        return administration.replaceWeekly(intervals);
    }

    @GetMapping("/blocks")
    List<AvailabilityBlockResponse> blocks() {
        return administration.listBlocks();
    }

    @PostMapping("/blocks")
    ResponseEntity<AvailabilityBlockResponse> createBlock(
            @Valid @RequestBody AvailabilityBlockRequest request) {
        AvailabilityBlockResponse response = administration.createBlock(request);
        return ResponseEntity
                .created(URI.create(
                        "/api/v1/admin/availability/blocks/" + response.id()))
                .body(response);
    }

    @DeleteMapping("/blocks/{id}")
    ResponseEntity<Void> deleteBlock(@PathVariable long id) {
        administration.deleteBlock(id);
        return ResponseEntity.noContent().build();
    }
}
