package com.amidog.app.admin;

import com.amidog.app.reservation.ReservationActor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminCancellationActorTests {

    @Test
    void representsFrozenV8MigrationCancellationsWithoutMakingMigrationOperational() {
        assertThat(AdminDtos.CancellationActor.fromStored("MIGRATION"))
                .isEqualTo(AdminDtos.CancellationActor.MIGRATION);
        assertThat(AdminDtos.CancellationActor.fromStored("CLIENT"))
                .isEqualTo(AdminDtos.CancellationActor.CLIENT);
        assertThat(AdminDtos.CancellationActor.fromStored("ADMIN"))
                .isEqualTo(AdminDtos.CancellationActor.ADMIN);
        assertThat(AdminDtos.CancellationActor.fromStored(null))
                .isNull();

        assertThatThrownBy(() ->
                ReservationActor.valueOf("MIGRATION"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
