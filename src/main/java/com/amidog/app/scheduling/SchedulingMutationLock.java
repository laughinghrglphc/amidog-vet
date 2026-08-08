package com.amidog.app.scheduling;

/**
 * Shared scheduling-write protocol.
 *
 * <p>Every transaction that can make a slot unavailable must acquire this
 * lock before its final overlap/availability read and before persisting the
 * mutation. Blackout and weekly replacements use it here. Reservation
 * creation/rescheduling in Task 4 must acquire this same component before
 * rechecking availability and inserting. The implementation is transaction
 * scoped; callers must never retain it across network or email work.</p>
 */
@FunctionalInterface
public interface SchedulingMutationLock {

    void acquire();
}
