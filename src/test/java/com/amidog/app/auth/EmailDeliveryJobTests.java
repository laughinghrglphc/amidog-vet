package com.amidog.app.auth;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailDeliveryJobTests {
 @Test void rejectionOrSendFailureLeavesARecoverableBackoffJob() {
  Instant now=Instant.parse("2026-07-29T00:00:00Z");
  EmailDeliveryJob job=EmailDeliveryJob.create(UserAccount.client("a@b.com","hash",now),EmailDeliveryType.PASSWORD_RESET,"1".repeat(64),now);
  // A freshly created leased job is delivery attempt zero; its first failed send schedules retry one.
  assertThat(job.retry(job.getDeliveryFence(), now)).isTrue();
  assertThat(job.getState()).isEqualTo(EmailDeliveryJob.State.PENDING);
  assertThat(job.getAttempts()).isEqualTo(1); assertThat(job.getNextAttemptAt()).isAfter(now);
 }
 @Test void successfulDeliveryCompletesTheDurableJob() {
  Instant now=Instant.parse("2026-07-29T00:00:00Z");
  EmailDeliveryJob job=EmailDeliveryJob.create(UserAccount.client("a@b.com","hash",now),EmailDeliveryType.VERIFICATION,"2".repeat(64),now);
  job.claim(now); job.complete(job.getDeliveryFence(), now);
  assertThat(job.getState()).isEqualTo(EmailDeliveryJob.State.COMPLETED);
  assertThat(job.getCompletedAt()).isEqualTo(now); assertThat(job.claim(now.plusSeconds(100))).isFalse();
 }
 @Test void leasePreventsTwoWorkersFromClaimingTheSameJobUntilExpiry() {
  Instant now=Instant.parse("2026-07-29T00:00:00Z"); EmailDeliveryJob job=EmailDeliveryJob.create(UserAccount.client("a@b.com","hash",now),EmailDeliveryType.PASSWORD_RESET,"3".repeat(64),now);
  assertThat(job.claim(now)).isFalse(); assertThat(job.claim(now.plusSeconds(1))).isFalse(); assertThat(job.claim(now.plusSeconds(61))).isTrue();
 }
 @Test void delayedCallbackWithOldFenceCannotCompleteOrReopenANewerAttempt() {
  Instant now=Instant.parse("2026-07-29T00:00:00Z"); EmailDeliveryJob job=EmailDeliveryJob.create(UserAccount.client("a@b.com","hash",now),EmailDeliveryType.PASSWORD_RESET,"4".repeat(64),now);
  String original=job.getDeliveryFence(); assertThat(job.claim(now.plusSeconds(61))).isTrue(); String replacement=job.getDeliveryFence();
  assertThat(original).isNotEqualTo(replacement); assertThat(job.complete(original,now.plusSeconds(62))).isFalse(); assertThat(job.retry(original,now.plusSeconds(62))).isFalse();
  assertThat(job.complete(replacement,now.plusSeconds(62))).isTrue();
 }
 @Test void fenceLossBeforePreparationCanBeDetectedWithoutIssuingANewToken() {
  Instant now=Instant.parse("2026-07-29T00:00:00Z"); EmailDeliveryJob job=EmailDeliveryJob.create(UserAccount.client("a@b.com","hash",now),EmailDeliveryType.PASSWORD_RESET,"5".repeat(64),now);
  String fencedAttempt=job.getDeliveryFence(); assertThat(job.claim(now.plusSeconds(61))).isTrue();
  assertThat(job.owns(fencedAttempt,now.plusSeconds(62))).isFalse();
 }

 @Test void factoryRejectsEveryNonSha256LowercaseHexShape() {
  Instant now=Instant.parse("2026-07-29T00:00:00Z");
  UserAccount user=UserAccount.client("a@b.com","hash",now);
  for (String invalid : new String[]{null, "a".repeat(63), "a".repeat(65),
          "A".repeat(64), "g".repeat(64)}) {
   assertThatThrownBy(() -> EmailDeliveryJob.create(
           user, EmailDeliveryType.PASSWORD_RESET, invalid, now))
           .isInstanceOf(IllegalArgumentException.class)
           .hasMessageContaining("64 lowercase hexadecimal");
  }
 }

 @Test void currentFenceReplacementRejectsInvalidExpectedOrReplacementHashes() {
  Instant now=Instant.parse("2026-07-29T00:00:00Z");
  EmailDeliveryJob job=EmailDeliveryJob.create(
          UserAccount.client("a@b.com","hash",now),EmailDeliveryType.PASSWORD_RESET,
          "a".repeat(64),now);
  String fence=job.getDeliveryFence();

  assertThatThrownBy(() -> job.replaceTokenHash(fence, null, "b".repeat(64), now))
          .isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(() -> job.replaceTokenHash(fence, "a".repeat(64), "B".repeat(64), now))
          .isInstanceOf(IllegalArgumentException.class);
  assertThat(job.getTokenHash()).isEqualTo("a".repeat(64));
 }

 @Test void completedHydratedLegacyNullHashCannotBeClaimedOrReopened() throws Exception {
  Instant now=Instant.parse("2026-07-29T00:00:00Z");
  EmailDeliveryJob job=EmailDeliveryJob.create(
          UserAccount.client("a@b.com","hash",now),EmailDeliveryType.PASSWORD_RESET,
          "a".repeat(64),now);
  String fence=job.getDeliveryFence();
  assertThat(job.complete(fence, now)).isTrue();
  var field=EmailDeliveryJob.class.getDeclaredField("tokenHash");
  field.setAccessible(true);
  field.set(job, null);

  assertThat(job.claim(now.plusSeconds(120))).isFalse();
  assertThat(job.retry(fence, now.plusSeconds(1))).isFalse();
  assertThat(job.getState()).isEqualTo(EmailDeliveryJob.State.COMPLETED);
 }
}
