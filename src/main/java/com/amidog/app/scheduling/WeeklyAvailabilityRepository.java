package com.amidog.app.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeeklyAvailabilityRepository
        extends JpaRepository<WeeklyAvailability, Long> {

    List<WeeklyAvailability>
    findAllByActiveTrueOrderByDayOfWeekAscLocalStartTimeAscLocalEndTimeAsc();

    List<WeeklyAvailability>
    findAllByOrderByDayOfWeekAscLocalStartTimeAscLocalEndTimeAsc();
}
