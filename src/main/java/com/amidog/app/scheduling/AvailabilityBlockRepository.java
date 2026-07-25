package com.amidog.app.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AvailabilityBlockRepository
        extends JpaRepository<AvailabilityBlock, Long> {

    List<AvailabilityBlock> findAllByOrderByStartAtAscEndAtAscIdAsc();

    @Query("""
            select block
            from AvailabilityBlock block
            where block.startAt < :end
              and block.endAt > :start
            order by block.startAt, block.endAt, block.id
            """)
    List<AvailabilityBlock> findOverlapping(
            @Param("start") Instant start,
            @Param("end") Instant end);
}
