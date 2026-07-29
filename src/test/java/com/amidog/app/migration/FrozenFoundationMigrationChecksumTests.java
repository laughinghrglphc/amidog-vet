package com.amidog.app.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FrozenFoundationMigrationChecksumTests {

    private static final Map<String, String> APPROVED_SHA256 = Map.of(
            "V1__account_and_session_foundation.sql",
            "F0F28F3524B7168AC605593208C99A4E3653376AA5088A8D535C9AD9E618DF99",
            "V2__email_delivery_outbox.sql",
            "8AE6EA396098251A1BE32CDA3200B9D436608700E774052DD2258C165C83F4F5",
            "V3__email_delivery_fencing.sql",
            "8FD6BDBFC22FECFC474C6BC804069857F89E59A5394EA1CFB5D3DBCC11012A57",
            "V4__single_administrator.sql",
            "59EE01C436444257A328AF04ACFCDC03DD3BCE6E693E8FCA0B35D55693ED325D",
            "V5__email_delivery_token_link.sql",
            "951F2DADB2144A661FA69AC8D8C6144FE00C17966C5BB683F95279169BCF26D5",
            "V6__email_delivery_token_hash_invariant.sql",
            "772350C3C3987A1A9AB69ACEAAE8248F76EA71183B05B04A2CCD9D451F7623BA"
    );

    @Test
    void foundationMigrationsRemainByteForByteCompatibleWithAppliedHistory()
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        for (Map.Entry<String, String> migration : APPROVED_SHA256.entrySet()) {
            byte[] bytes = new ClassPathResource(
                    "db/migration/" + migration.getKey())
                    .getInputStream()
                    .readAllBytes();
            String actual = HexFormat.of().withUpperCase()
                    .formatHex(digest.digest(bytes));
            assertThat(actual)
                    .as("SHA-256 for frozen migration %s", migration.getKey())
                    .isEqualTo(migration.getValue());
        }
    }
}
