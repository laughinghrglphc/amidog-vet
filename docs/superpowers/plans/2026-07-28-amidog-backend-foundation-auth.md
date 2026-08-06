# AmiDog Backend Foundation and Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Phase 1 persistence/security skeleton with a migration-controlled PostgreSQL foundation and verified email/password accounts for clients plus one administrator.

**Architecture:** Keep one Spring Boot modular monolith, but move domain code into package-by-feature modules. Flyway owns the schema, Spring Security owns password authentication and session fixation protection, Spring Session stores sessions in PostgreSQL, and account emails are emitted through an `EmailSender` boundary after database commit.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring MVC, Spring Data JPA, Spring Security, Spring Session JDBC, Spring Mail, Flyway, PostgreSQL 17, JUnit 5, MockMvc, Testcontainers 2.x.

**Applied migration ledger (frozen after foundation acceptance):** V1 account,
client, pet, token, and session foundation; V2 durable email outbox; V3 outbox
lease fencing; V4 single-administrator database guarantee; V5 exact
token-to-delivery-job linkage; V6 durable token-hash shape/state invariant.
Never edit V1-V6 after application. Scheduling migrations start at V7.

## Global Constraints

- The clinic timezone is exactly `America/Santiago`; never hard-code UTC-3 or UTC-4.
- Production Hibernate mode is `ddl-auto: validate`; Flyway is the only schema authority.
- Passwords use Spring Security's adaptive delegating encoder and are never logged.
- Verification and reset tokens are random, expiring, single-use values; only SHA-256 hashes are stored.
- Client APIs derive ownership from the authenticated session and never accept an arbitrary client ID.
- There is one `ADMIN` account and no public administrator-registration endpoint.
- Google login remains inactive; only the schema extension point is created.
- Public error responses do not reveal whether an email address is registered.
- The uploaded project has no `.git`; never initialize Git implicitly.

---

## File Structure

### Build and runtime configuration

- Modify `pom.xml` — add Flyway, mail, JDBC sessions, and PostgreSQL Testcontainers; remove H2-only integration testing.
- Modify `src/main/resources/application.yaml` — validated schema, session cookies, mail, administrator bootstrap, and application properties.
- Modify `src/test/resources/application.yaml` — Testcontainers-compatible test settings with external delivery disabled.
- Modify `.env.example` — document database, administrator, SMTP, frontend URL, and clinic settings without secrets.
- Create `src/main/java/com/amidog/app/config/AmidogProperties.java` — typed application configuration.
- Create `src/main/java/com/amidog/app/config/TimeConfig.java` — one injectable UTC `Clock`.

### Shared backend infrastructure

- Create `src/main/java/com/amidog/app/common/api/ApiErrorResponse.java`.
- Create `src/main/java/com/amidog/app/common/api/ApiExceptionHandler.java`.
- Create `src/main/java/com/amidog/app/common/api/ConflictException.java`.
- Create `src/main/java/com/amidog/app/common/api/NotFoundException.java`.
- Create `src/main/java/com/amidog/app/common/api/TooManyRequestsException.java`.
- Create `src/main/java/com/amidog/app/common/security/RateLimitService.java`.
- Create `src/test/java/com/amidog/app/support/PostgresIntegrationTest.java`.
- Create `src/test/java/com/amidog/app/support/PostgresTestConfiguration.java`.
- Create `src/test/java/com/amidog/app/support/AccountTestFixtures.java`.
- Create `src/test/java/com/amidog/app/support/SessionFixture.java`.
- Create `src/test/java/com/amidog/app/support/CapturingEmailSender.java`.

### Authentication and account domain

- Create `src/main/java/com/amidog/app/auth/AccountType.java`.
- Create `src/main/java/com/amidog/app/auth/UserAccount.java`.
- Create `src/main/java/com/amidog/app/auth/UserExternalIdentity.java`.
- Create `src/main/java/com/amidog/app/auth/UserAccountRepository.java`.
- Create `src/main/java/com/amidog/app/auth/UserExternalIdentityRepository.java`.
- Create `src/main/java/com/amidog/app/auth/EmailVerificationToken.java`.
- Create `src/main/java/com/amidog/app/auth/EmailVerificationTokenRepository.java`.
- Create `src/main/java/com/amidog/app/auth/PasswordResetToken.java`.
- Create `src/main/java/com/amidog/app/auth/PasswordResetTokenRepository.java`.
- Create `src/main/java/com/amidog/app/auth/SecureTokenService.java`.
- Create `src/main/java/com/amidog/app/auth/AccountPrincipal.java`.
- Create `src/main/java/com/amidog/app/auth/AccountUserDetailsService.java`.
- Create `src/main/java/com/amidog/app/auth/RegistrationService.java`.
- Create `src/main/java/com/amidog/app/auth/PasswordRecoveryService.java`.
- Create `src/main/java/com/amidog/app/auth/AuthController.java`.
- Create `src/main/java/com/amidog/app/auth/AuthDtos.java`.
- Create `src/main/java/com/amidog/app/auth/AdminAccountBootstrap.java`.

### Client identity

- Create `src/main/java/com/amidog/app/client/Client.java`.
- Create `src/main/java/com/amidog/app/client/ClientRepository.java`.
- Create `src/main/java/com/amidog/app/client/Pet.java`.
- Create `src/main/java/com/amidog/app/client/PetRepository.java`.

### Email boundary

- Create `src/main/java/com/amidog/app/email/EmailMessage.java`.
- Create `src/main/java/com/amidog/app/email/EmailSender.java`.
- Create `src/main/java/com/amidog/app/email/LoggingEmailSender.java`.
- Create `src/main/java/com/amidog/app/email/SmtpEmailSender.java`.
- Create `src/main/java/com/amidog/app/auth/AccountEmailListener.java`.

### Database

- Create `src/main/resources/db/migration/V1__account_and_session_foundation.sql`.
- Create `src/test/java/com/amidog/app/migration/FlywayMigrationIntegrationTests.java`.

### Retired Phase 1 files

- Delete the old `src/main/java/com/amidog/app/models/*` graph after replacement classes compile.
- Delete the old `src/main/java/com/amidog/app/repositories/*` interfaces after callers move.
- Delete `src/main/java/com/amidog/app/controllers/CreateReservationController.java`, `src/main/java/com/amidog/app/controllers/ApiExceptionHandler.java`, `src/main/java/com/amidog/app/dtos/*`, and `src/main/java/com/amidog/app/services/ReservationService.java` after the compatibility test is deliberately replaced in the scheduling plan.

## Task 1: Migration-Controlled PostgreSQL Test Foundation

**Files:**

- Modify: `pom.xml`
- Modify: `src/main/resources/application.yaml`
- Modify: `src/test/resources/application.yaml`
- Create: `src/main/resources/db/migration/V1__account_and_session_foundation.sql`
- Create: `src/test/java/com/amidog/app/support/PostgresIntegrationTest.java`
- Create: `src/test/java/com/amidog/app/support/PostgresTestConfiguration.java`
- Create: `src/test/java/com/amidog/app/migration/FlywayMigrationIntegrationTests.java`

**Interfaces:**

- Produces: a reusable PostgreSQL 17 test application context and the account/session schema required by all later tasks.

- [ ] **Step 1: Add the failing migration integration test**

```java
package com.amidog.app.migration;

import com.amidog.app.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    JdbcClient jdbc;

    @Test
    void createsAccountClientPetTokenAndSessionTables() {
        Integer count = jdbc.sql("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in (
                    'users', 'user_external_identities', 'clients', 'pets',
                    'email_verification_tokens', 'password_reset_tokens',
                    'spring_session', 'spring_session_attributes'
                  )
                """).query(Integer.class).single();

        assertThat(count).isEqualTo(8);
    }
}
```

- [ ] **Step 2: Run the test and verify the missing Testcontainers/schema failure**

Run:

```powershell
.\mvnw.cmd -Dtest=FlywayMigrationIntegrationTests test
```

Expected: FAIL because the PostgreSQL test configuration and Flyway migration do not exist.

- [ ] **Step 3: Add managed dependencies and the PostgreSQL test container**

Add these dependencies to `pom.xml` without explicit versions because Spring Boot 4.1 manages them:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-session-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-mail</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

Remove the H2 dependency and create:

```java
package com.amidog.app.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("amidog_test")
                .withUsername("amidog")
                .withPassword("amidog");
    }
}
```

```java
package com.amidog.app.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
public abstract class PostgresIntegrationTest {
}
```

Configure both runtime profiles to let Flyway initialize the application tables while Spring Session initialization remains disabled:

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
  session:
    jdbc:
      initialize-schema: never
amidog:
  clinic-zone: ${CLINIC_TIMEZONE:America/Santiago}
  frontend-base-url: ${FRONTEND_BASE_URL:http://localhost:5173}
  booking:
    auto-confirm: ${BOOKING_AUTO_CONFIRM:false}
    duration-minutes: ${BOOKING_DURATION_MINUTES:30}
    minimum-notice-hours: ${BOOKING_MIN_NOTICE_HOURS:2}
    horizon-days: ${BOOKING_HORIZON_DAYS:90}
  admin:
    email: ${ADMIN_EMAIL:}
    password: ${ADMIN_PASSWORD:}
    name: ${ADMIN_NAME:Administradora AmiDog}
    phone: ${ADMIN_PHONE:}
  contact:
    clinic-email: ${CLINIC_EMAIL:}
    whatsapp-number: ${WHATSAPP_NUMBER:}
  email:
    delivery: ${EMAIL_DELIVERY:log}
    smtp:
      host: ${SMTP_HOST:}
      port: ${SMTP_PORT:587}
      username: ${SMTP_USERNAME:}
      password: ${SMTP_PASSWORD:}
      auth: ${SMTP_AUTH:true}
      starttls: ${SMTP_STARTTLS:true}
      starttls-required: ${SMTP_STARTTLS_REQUIRED:true}
      ssl: ${SMTP_SSL:false}
      allow-plaintext: ${SMTP_ALLOW_PLAINTEXT:false}
```

- [ ] **Step 4: Create the complete V1 schema**

`V1__account_and_session_foundation.sql` must contain:

```sql
do $$
begin
    if exists (
        select 1 from information_schema.columns
        where table_schema='public' and table_name='clients' and column_name='id_client'
    ) then
        alter table clients rename to legacy_clients;
    end if;
    if exists (
        select 1 from information_schema.columns
        where table_schema='public' and table_name='pets' and column_name='id_pet'
    ) then
        alter table pets rename to legacy_pets;
    end if;
    if exists (
        select 1 from information_schema.columns
        where table_schema='public' and table_name='reservations' and column_name='id_reservation'
    ) then
        alter table reservations rename to legacy_reservations;
    end if;
    if to_regclass('public.reservation_pets') is not null then
        alter table reservation_pets rename to legacy_reservation_pets;
    end if;
end $$;

create table users (
    id bigint generated by default as identity primary key,
    email_normalized varchar(254) not null unique,
    password_hash varchar(255),
    email_verified_at timestamptz,
    account_type varchar(20) not null check (account_type in ('CLIENT', 'ADMIN')),
    enabled boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create table user_external_identities (
    id bigint generated by default as identity primary key,
    user_id bigint not null references users(id),
    provider varchar(30) not null,
    provider_subject varchar(255) not null,
    created_at timestamptz not null default now(),
    unique (provider, provider_subject)
);

create table clients (
    id bigint generated by default as identity primary key,
    user_id bigint not null unique references users(id),
    name varchar(120) not null,
    phone varchar(30) not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create table pets (
    id bigint generated by default as identity primary key,
    client_id bigint not null references clients(id),
    name varchar(80) not null,
    species varchar(40) not null,
    breed varchar(80),
    birthdate date,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);
create index pets_client_active_idx on pets(client_id, active);

create table email_verification_tokens (
    id bigint generated by default as identity primary key,
    user_id bigint not null references users(id) on delete cascade,
    token_hash char(64) not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now()
);
create index email_verification_user_idx on email_verification_tokens(user_id);

create table password_reset_tokens (
    id bigint generated by default as identity primary key,
    user_id bigint not null references users(id) on delete cascade,
    token_hash char(64) not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now()
);
create index password_reset_user_idx on password_reset_tokens(user_id);

create table spring_session (
    primary_id char(36) not null primary key,
    session_id char(36) not null,
    creation_time bigint not null,
    last_access_time bigint not null,
    max_inactive_interval integer not null,
    expiry_time bigint not null,
    principal_name varchar(100)
);
create unique index spring_session_ix1 on spring_session(session_id);
create index spring_session_ix2 on spring_session(expiry_time);
create index spring_session_ix3 on spring_session(principal_name);

create table spring_session_attributes (
    session_primary_id char(36) not null
        references spring_session(primary_id) on delete cascade,
    attribute_name varchar(200) not null,
    attribute_bytes bytea not null,
    primary key (session_primary_id, attribute_name)
);
```

- [ ] **Step 5: Run the migration test and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=FlywayMigrationIntegrationTests test
if (Test-Path .git) {
  git add pom.xml src/main/resources src/test/resources src/test/java/com/amidog/app/support src/test/java/com/amidog/app/migration
  git commit -m "build: establish PostgreSQL migration test foundation"
}
```

Expected: PASS with Flyway applying version 1 to PostgreSQL 17.

## Task 2: Typed Configuration and Account Entities

**Files:**

- Create: `src/main/java/com/amidog/app/config/AmidogProperties.java`
- Create: `src/main/java/com/amidog/app/auth/AccountType.java`
- Create: `src/main/java/com/amidog/app/auth/UserAccount.java`
- Create: `src/main/java/com/amidog/app/auth/UserExternalIdentity.java`
- Create: `src/main/java/com/amidog/app/auth/UserAccountRepository.java`
- Create: `src/main/java/com/amidog/app/auth/UserExternalIdentityRepository.java`
- Create: `src/main/java/com/amidog/app/client/Client.java`
- Create: `src/main/java/com/amidog/app/client/ClientRepository.java`
- Create: `src/main/java/com/amidog/app/client/Pet.java`
- Create: `src/main/java/com/amidog/app/client/PetRepository.java`
- Create: `src/test/java/com/amidog/app/auth/AccountPersistenceIntegrationTests.java`
- Modify: `src/main/java/com/amidog/app/AppApplication.java`

**Interfaces:**

- Produces: `UserAccountRepository.findByEmailNormalized(String)`, `ClientRepository.findByUserId(Long)`, and `AmidogProperties`.

- [ ] **Step 1: Write the failing account persistence test**

```java
class AccountPersistenceIntegrationTests extends PostgresIntegrationTest {

    @Autowired UserAccountRepository users;
    @Autowired ClientRepository clients;

    @Test
    @Transactional
    void storesAClientAgainstOneNormalizedUserAccount() {
        UserAccount user = users.save(UserAccount.client(
                "ana@example.com", "{noop}not-used-in-production", Instant.now()));
        Client client = clients.save(Client.create(user, "Ana Pérez", "+56912345678", Instant.now()));

        assertThat(users.findByEmailNormalized("ana@example.com")).contains(user);
        assertThat(clients.findByUserId(user.getId())).contains(client);
    }
}
```

- [ ] **Step 2: Run the test and verify the missing types**

Run:

```powershell
.\mvnw.cmd -Dtest=AccountPersistenceIntegrationTests test
```

Expected: compilation FAIL because the account and client domain types do not exist.

- [ ] **Step 3: Implement focused entities and repositories**

Use `Long` consistently for all new identifiers. `UserAccount` exposes factory methods instead of public all-arguments constructors:

```java
public enum AccountType {
    CLIENT, ADMIN
}
```

```java
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "email_normalized", nullable = false, unique = true, length = 254)
    private String emailNormalized;
    @Column(name = "password_hash", length = 255)
    private String passwordHash;
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    public static UserAccount client(String email, String passwordHash, Instant now) {
        return new UserAccount(email, passwordHash, AccountType.CLIENT, false, now);
    }

    public static UserAccount admin(String email, String passwordHash, Instant now) {
        UserAccount account = new UserAccount(email, passwordHash, AccountType.ADMIN, true, now);
        account.emailVerifiedAt = now;
        return account;
    }

    private UserAccount(String email, String hash, AccountType type, boolean enabled, Instant now) {
        this.emailNormalized = email;
        this.passwordHash = hash;
        this.accountType = type;
        this.enabled = enabled;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void verify(Instant now) {
        emailVerifiedAt = now;
        enabled = true;
        updatedAt = now;
    }

    public void replacePassword(String hash, Instant now) {
        passwordHash = hash;
        updatedAt = now;
    }

    public boolean isVerified() {
        return emailVerifiedAt != null;
    }
}
```

`Client` and `Pet` have only child-to-parent relations; do not add bidirectional collections:

```java
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailNormalized(String emailNormalized);
    List<UserAccount> findAllByAccountType(AccountType accountType);
}

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByUserId(Long userId);
}

public interface PetRepository extends JpaRepository<Pet, Long> {
    List<Pet> findAllByClientIdAndActiveTrueOrderByNameAsc(Long clientId);
}
```

- [ ] **Step 4: Add typed properties and pass the test**

Enable configuration properties in `AppApplication` and define:

```java
@ConfigurationProperties(prefix = "amidog")
public record AmidogProperties(
        ZoneId clinicZone,
        URI frontendBaseUrl,
        Booking booking,
        Admin admin,
        Contact contact,
        Email email
) {
    public record Booking(boolean autoConfirm, int durationMinutes,
                          int minimumNoticeHours, int horizonDays) {}
    public record Admin(String email, String password, String name, String phone) {}
    public record Contact(String clinicEmail, String whatsappNumber) {}
    public record Email(String delivery) {}
}
```

Validate `booking.durationMinutes` as exactly `30`, `minimumNoticeHours` as nonnegative, and `horizonDays` as positive so configuration cannot disagree with the database duration constraint.

Create the production time source:

```java
@Configuration
public class TimeConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

Run:

```powershell
.\mvnw.cmd -Dtest=AccountPersistenceIntegrationTests test
```

Expected: PASS and Hibernate validation succeeds against Flyway V1.

- [ ] **Step 5: Checkpoint**

```powershell
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/config src/main/java/com/amidog/app/auth src/main/java/com/amidog/app/client src/test/java/com/amidog/app/auth
  git commit -m "feat: add account and client identity domain"
}
```

## Task 3: Secure Single-Use Account Tokens and Email Boundary

**Files:**

- Create: `src/main/java/com/amidog/app/auth/EmailVerificationToken.java`
- Create: `src/main/java/com/amidog/app/auth/EmailVerificationTokenRepository.java`
- Create: `src/main/java/com/amidog/app/auth/PasswordResetToken.java`
- Create: `src/main/java/com/amidog/app/auth/PasswordResetTokenRepository.java`
- Create: `src/main/java/com/amidog/app/auth/SecureTokenService.java`
- Create: `src/main/java/com/amidog/app/email/EmailMessage.java`
- Create: `src/main/java/com/amidog/app/email/EmailSender.java`
- Create: `src/main/java/com/amidog/app/email/LoggingEmailSender.java`
- Create: `src/main/java/com/amidog/app/email/SmtpEmailSender.java`
- Create: `src/test/java/com/amidog/app/auth/SecureTokenServiceTests.java`

**Interfaces:**

- Produces: `SecureTokenService.issue() -> IssuedToken`, `SecureTokenService.hash(String)`, and `EmailSender.send(EmailMessage)`.

- [ ] **Step 1: Write token behavior tests**

```java
class SecureTokenServiceTests {
    private final SecureTokenService tokens = new SecureTokenService(new SecureRandom());

    @Test
    void storesAStableHashInsteadOfTheRawToken() {
        IssuedToken issued = tokens.issue();

        assertThat(issued.raw()).doesNotContain("=");
        assertThat(issued.hash()).hasSize(64);
        assertThat(tokens.hash(issued.raw())).isEqualTo(issued.hash());
        assertThat(issued.hash()).doesNotContain(issued.raw());
    }

    @Test
    void twoIssuedTokensAreDifferent() {
        assertThat(tokens.issue().raw()).isNotEqualTo(tokens.issue().raw());
    }
}
```

- [ ] **Step 2: Run and verify failure**

Run:

```powershell
.\mvnw.cmd -Dtest=SecureTokenServiceTests test
```

Expected: compilation FAIL because `SecureTokenService` and `IssuedToken` do not exist.

- [ ] **Step 3: Implement token generation and persistence entities**

```java
@Service
public class SecureTokenService {
    private final SecureRandom random;

    public SecureTokenService() {
        this(new SecureRandom());
    }

    SecureTokenService(SecureRandom random) {
        this.random = random;
    }

    public IssuedToken issue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new IssuedToken(raw, hash(raw));
    }

    public String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedToken(String raw, String hash) {}
}
```

Both token entities map `user_id`, `token_hash`, `expires_at`, `consumed_at`, and `created_at`, and expose:

```java
boolean canConsume(Instant now) {
    return consumedAt == null && expiresAt.isAfter(now);
}

void consume(Instant now) {
    consumedAt = now;
}
```

Repositories expose `findByTokenHash(String)` plus invalidation updates scoped to a user.

- [ ] **Step 4: Implement provider-neutral email delivery**

```java
public record EmailMessage(String to, String subject, String text, String replyTo) {
    public EmailMessage {
        Objects.requireNonNull(to);
        Objects.requireNonNull(subject);
        Objects.requireNonNull(text);
    }
}
```

```java
public interface EmailSender {
    void send(EmailMessage message);
}
```

`LoggingEmailSender` uses `@ConditionalOnProperty(name = "amidog.email.delivery", havingValue = "log", matchIfMissing = true)`, logs only recipient and subject, and never logs links or tokens. `SmtpEmailSender` uses the same condition with `havingValue = "smtp"`, builds a `SimpleMailMessage`, sets `replyTo` only when present, and delegates to `JavaMailSender`.

Run:

```powershell
.\mvnw.cmd -Dtest=SecureTokenServiceTests test
```

Expected: PASS.

- [ ] **Step 5: Checkpoint**

```powershell
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/auth src/main/java/com/amidog/app/email src/test/java/com/amidog/app/auth
  git commit -m "feat: add secure account tokens and email boundary"
}
```

## Task 4: Registration, Verification, and Resend

**Files:**

- Create: `src/main/java/com/amidog/app/auth/AuthDtos.java`
- Create: `src/main/java/com/amidog/app/auth/RegistrationService.java`
- Create: `src/main/java/com/amidog/app/auth/AccountEmailListener.java`
- Create: `src/main/java/com/amidog/app/auth/AuthController.java`
- Create: `src/test/java/com/amidog/app/auth/RegistrationIntegrationTests.java`

**Interfaces:**

- Consumes: account/client repositories, `SecureTokenService`, `PasswordEncoder`, `EmailSender`, and `AmidogProperties.frontendBaseUrl`.
- Produces: `POST /api/v1/auth/register`, `POST /api/v1/auth/verify-email`, and `POST /api/v1/auth/resend-verification`.

- [ ] **Step 1: Write registration and verification API tests**

```java
class RegistrationIntegrationTests extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository users;
    @Autowired EmailVerificationTokenRepository verificationTokens;

    @Test
    void registersAnUnverifiedClientAndStoresOnlyAHash() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":" Ana@Example.com ","password":"Correct-Horse-9!",
                     "name":"Ana Pérez","phone":"+56 9 1234 5678"}
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.message").value("Revisa tu correo para verificar tu cuenta."));

        UserAccount user = users.findByEmailNormalized("ana@example.com").orElseThrow();
        assertThat(user.isVerified()).isFalse();
        assertThat(verificationTokens.findAll()).singleElement()
                .extracting(EmailVerificationToken::getTokenHash)
                .asString().hasSize(64);
    }

    @Test
    void consumesAValidTokenOnce() throws Exception {
        String raw = registerAndReadRawTokenFromTestEmail();

        mvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonToken(raw)))
            .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/verify-email").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonToken(raw)))
            .andExpect(status().isBadRequest());
    }
}
```

Create `CapturingEmailSender` as a thread-safe test bean:

```java
public final class CapturingEmailSender implements EmailSender {
    private final List<EmailMessage> messages = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        messages.add(message);
    }

    public List<EmailMessage> messages() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
    }
}
```

Import it through a `@TestConfiguration` with `@Primary`. `AccountTestFixtures` creates verified/unverified client accounts and an administrator, obtains authenticated `MockHttpSession` values through MockMvc login, and returns:

```java
public record SessionFixture(Long userId, Long clientId, MockHttpSession session) {}
```

- [ ] **Step 2: Run and verify endpoint failure**

Run:

```powershell
.\mvnw.cmd -Dtest=RegistrationIntegrationTests test
```

Expected: FAIL with `404` because the authentication API is absent.

- [ ] **Step 3: Implement validated DTO contracts**

`AuthDtos` contains:

```java
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 30) String phone) {}

public record TokenRequest(@NotBlank @Size(max = 256) String token) {}
public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {}
public record MessageResponse(String message) {}
```

Normalize email using `trim().toLowerCase(Locale.ROOT)` in one private service method.

- [ ] **Step 4: Implement transactional registration and after-commit email**

`RegistrationService.register(RegisterRequest)`:

1. returns the same accepted result if the normalized email exists;
2. encodes the password;
3. saves disabled/unverified `UserAccount` and `Client`;
4. stores a verification hash expiring in 24 hours;
5. publishes `VerificationEmailRequested(userId, rawToken)`.

`AccountEmailListener` uses `@TransactionalEventListener(phase = AFTER_COMMIT)` and sends:

```java
URI verifyUrl = properties.frontendBaseUrl().resolve(
        "/verificar-correo?token=" + URLEncoder.encode(rawToken, UTF_8));
emailSender.send(new EmailMessage(
        user.getEmailNormalized(),
        "Verifica tu cuenta AmiDog",
        "Verifica tu cuenta usando este enlace: " + verifyUrl,
        null));
```

`verify(rawToken)` hashes the raw value, locks the token row, rejects expired/consumed values, consumes it, and calls `user.verify(clock.instant())`.

`resend(email)` always returns accepted; for an existing unverified client it consumes prior active tokens and issues one new token.

- [ ] **Step 5: Pass the API tests and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=RegistrationIntegrationTests test
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/auth src/test/java/com/amidog/app/auth
  git commit -m "feat: add verified client registration"
}
```

Expected: PASS, including single-use and expiration cases.

## Task 5: Session Login, Authorization, Logout, and CSRF

**Files:**

- Create: `src/main/java/com/amidog/app/auth/AccountPrincipal.java`
- Create: `src/main/java/com/amidog/app/auth/AccountUserDetailsService.java`
- Modify: `src/main/java/com/amidog/app/config/SecurityConfig.java`
- Modify: `src/main/java/com/amidog/app/auth/AuthController.java`
- Create: `src/test/java/com/amidog/app/auth/SessionSecurityIntegrationTests.java`

**Interfaces:**

- Produces: form-encoded `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, and `GET /api/v1/auth/csrf`.

- [ ] **Step 1: Write session and authorization tests**

```java
@Test
void verifiedClientCanLoginAndReuseTheDatabaseSession() throws Exception {
    createVerifiedClient("ana@example.com", "Correct-Horse-9!");

    MvcResult login = mvc.perform(post("/api/v1/auth/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", "ana@example.com")
            .param("password", "Correct-Horse-9!"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountType").value("CLIENT"))
        .andReturn();

    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    mvc.perform(get("/api/v1/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ana@example.com"));
}

@Test
void unverifiedClientReceivesTheSameGenericLoginFailure() throws Exception {
    createUnverifiedClient("ana@example.com", "Correct-Horse-9!");

    mvc.perform(post("/api/v1/auth/login").with(csrf())
            .param("username", "ana@example.com")
            .param("password", "Correct-Horse-9!"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message")
            .value("Correo o contraseña incorrectos, o cuenta aún no verificada."));
}
```

- [ ] **Step 2: Run and verify failure**

Run:

```powershell
.\mvnw.cmd -Dtest=SessionSecurityIntegrationTests test
```

Expected: FAIL because login still uses the Phase 1 HTTP Basic configuration.

- [ ] **Step 3: Implement `UserDetailsService` and JSON handlers**

`AccountPrincipal` implements `UserDetails`, uses the normalized email as username, and emits `ROLE_CLIENT` or `ROLE_ADMIN`. `isEnabled()` returns `account.isEnabled() && account.isVerified()`.

Configure:

```java
http
    .csrf(csrf -> csrf.csrfTokenRepository(
            CookieCsrfTokenRepository.withHttpOnlyFalse()))
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.GET,
            "/api/v1/auth/csrf", "/api/v1/services", "/api/v1/availability").permitAll()
        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/verify-email",
            "/api/v1/auth/resend-verification", "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password", "/api/v1/contact").permitAll()
        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/v1/me/**").hasRole("CLIENT")
        .anyRequest().authenticated())
    .formLogin(login -> login
        .loginProcessingUrl("/api/v1/auth/login")
        .successHandler((request, response, authentication) ->
            writeJson(response, HttpStatus.OK, AuthResponse.from(authentication)))
        .failureHandler((request, response, exception) ->
            writeJson(response, HttpStatus.UNAUTHORIZED,
                new MessageResponse("Correo o contraseña incorrectos, o cuenta aún no verificada."))))
    .logout(logout -> logout
        .logoutUrl("/api/v1/auth/logout")
        .deleteCookies("SESSION", "XSRF-TOKEN")
        .logoutSuccessHandler((request, response, authentication) ->
            response.setStatus(HttpStatus.NO_CONTENT.value())))
    .httpBasic(AbstractHttpConfigurer::disable)
    .requestCache(AbstractHttpConfigurer::disable);
```

Define a `PasswordEncoder` using `PasswordEncoderFactories.createDelegatingPasswordEncoder()`.

- [ ] **Step 4: Return current account and CSRF state**

`GET /api/v1/auth/me` returns:

```java
public record AuthResponse(Long userId, Long clientId, String email,
                           String name, AccountType accountType) {}
```

`GET /api/v1/auth/csrf` accepts the injected `CsrfToken` and returns:

```java
public record CsrfResponse(String headerName, String parameterName, String token) {}
```

Run:

```powershell
.\mvnw.cmd -Dtest=SessionSecurityIntegrationTests test
```

Expected: PASS for login, session reuse, role checks, CSRF rejection, logout, and unverified account rejection.

- [ ] **Step 5: Checkpoint**

```powershell
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/auth src/main/java/com/amidog/app/config/SecurityConfig.java src/test/java/com/amidog/app/auth
  git commit -m "feat: add secure JDBC session authentication"
}
```

## Task 6: Password Recovery

**Files:**

- Create: `src/main/java/com/amidog/app/auth/PasswordRecoveryService.java`
- Modify: `src/main/java/com/amidog/app/auth/AccountEmailListener.java`
- Modify: `src/main/java/com/amidog/app/auth/AuthController.java`
- Modify: `src/main/java/com/amidog/app/auth/AuthDtos.java`
- Create: `src/test/java/com/amidog/app/auth/PasswordRecoveryIntegrationTests.java`

**Interfaces:**

- Produces: `POST /api/v1/auth/forgot-password` and `POST /api/v1/auth/reset-password`.

- [ ] **Step 1: Write recovery tests**

```java
@Test
void forgotPasswordIsNonEnumeratingAndResetConsumesTheToken() throws Exception {
    createVerifiedClient("ana@example.com", "Old-Password-9!");

    mvc.perform(post("/api/v1/auth/forgot-password").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"ana@example.com"}"""))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message")
            .value("Si la cuenta existe, enviaremos instrucciones al correo."));

    String raw = capturedEmail.resetToken();
    mvc.perform(post("/api/v1/auth/reset-password").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(resetJson(raw, "New-Password-10!")))
        .andExpect(status().isNoContent());

    assertThat(passwordEncoder.matches("New-Password-10!",
        users.findByEmailNormalized("ana@example.com").orElseThrow().getPasswordHash())).isTrue();
}
```

- [ ] **Step 2: Run and verify `404`**

Run:

```powershell
.\mvnw.cmd -Dtest=PasswordRecoveryIntegrationTests test
```

Expected: FAIL because recovery endpoints do not exist.

- [ ] **Step 3: Implement recovery and reset**

Add:

```java
public record ResetPasswordRequest(
        @NotBlank @Size(max = 256) String token,
        @NotBlank @Size(min = 12, max = 128) String password) {}
```

`requestReset(email)` always returns the same accepted response. For a verified, enabled account it consumes active reset tokens, stores a new hash with a one-hour expiry, and publishes `PasswordResetEmailRequested`.

`reset(token, password)` locks by hash, checks expiration and consumption, replaces the encoded password, consumes the token, and invalidates every other active reset token for that user.

- [ ] **Step 4: Pass recovery, expiry, and reuse tests**

Run:

```powershell
.\mvnw.cmd -Dtest=PasswordRecoveryIntegrationTests test
```

Expected: PASS for existing/unknown emails, expired token, consumed token, and successful login with the new password.

- [ ] **Step 5: Checkpoint**

```powershell
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/auth src/test/java/com/amidog/app/auth
  git commit -m "feat: add non-enumerating password recovery"
}
```

## Task 7: Rate Limits, Unified Errors, and Administrator Bootstrap

**Files:**

- Create: `src/main/java/com/amidog/app/common/api/ApiErrorResponse.java`
- Create: `src/main/java/com/amidog/app/common/api/ApiExceptionHandler.java`
- Create: `src/main/java/com/amidog/app/common/api/ConflictException.java`
- Create: `src/main/java/com/amidog/app/common/api/NotFoundException.java`
- Create: `src/main/java/com/amidog/app/common/api/TooManyRequestsException.java`
- Create: `src/main/java/com/amidog/app/common/security/RateLimitService.java`
- Create: `src/main/java/com/amidog/app/auth/AdminAccountBootstrap.java`
- Modify: `src/main/java/com/amidog/app/auth/AuthController.java`
- Modify: `.env.example`
- Create: `src/test/java/com/amidog/app/auth/AdminBootstrapIntegrationTests.java`
- Create: `src/test/java/com/amidog/app/common/security/RateLimitServiceTests.java`

**Interfaces:**

- Produces: consistent `{code,message,errors}` failures, fixed-window per-operation limits, and idempotent creation of the single administrator.

- [ ] **Step 1: Write failing rate-limit and bootstrap tests**

```java
@Test
void rejectsTheSixthAttemptInsideOneMinute() {
    RateLimitService limits = new RateLimitService(testClock);
    IntStream.range(0, 5).forEach(index ->
        assertThat(limits.tryAcquire("login:127.0.0.1", 5, Duration.ofMinutes(1))).isTrue());
    assertThat(limits.tryAcquire("login:127.0.0.1", 5, Duration.ofMinutes(1))).isFalse();
}
```

```java
@Test
void seedsExactlyOneVerifiedAdministrator() {
    bootstrap.run();
    bootstrap.run();

assertThat(users.findAllByAccountType(AccountType.ADMIN)).singleElement()
        .satisfies(admin -> {
            assertThat(admin.isVerified()).isTrue();
            assertThat(admin.isEnabled()).isTrue();
        });
assertThat(clients.count()).isZero();
}
```

- [ ] **Step 2: Run and verify missing implementation**

Run:

```powershell
.\mvnw.cmd -Dtest=RateLimitServiceTests,AdminBootstrapIntegrationTests test
```

Expected: compilation FAIL for the new services.

- [ ] **Step 3: Implement exact error and rate-limit behavior**

```java
public record ApiErrorResponse(
        String code,
        String message,
        Map<String, String> errors) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, Map.of());
    }
}
```

`RateLimitService` stores `Window(start,count)` in a `ConcurrentHashMap<String, Window>`, resets at the configured duration, and removes stale entries every 1,000 acquisitions. Controller policies:

- register: 5 per remote address per hour;
- login: 10 per remote address per 15 minutes;
- verification resend: 3 per normalized email per hour;
- forgot password: 3 per normalized email per hour.

A rejected acquisition throws `TooManyRequestsException`; the advice returns `429` with code `RATE_LIMITED`.

- [ ] **Step 4: Implement idempotent administrator creation**

`AdminAccountBootstrap` is an `ApplicationRunner` active only when `amidog.admin.email` and `amidog.admin.password` are nonblank. It:

1. normalizes the email;
2. returns if an `ADMIN` already exists;
3. refuses to convert an existing `CLIENT` email;
4. creates only a verified `UserAccount.admin(...)`; administrator display name comes from typed configuration and the administrator is never counted as a clinic client;
5. logs only that bootstrap completed, not the email or password.

Document exact environment names:

```properties
DB_URL=jdbc:postgresql://localhost:5432/amidog
DB_USERNAME=postgres
DB_PASSWORD=replace-me
ADMIN_EMAIL=admin@example.cl
ADMIN_PASSWORD=replace-with-at-least-12-characters
ADMIN_NAME=Administradora AmiDog
ADMIN_PHONE=+56900000000
FRONTEND_BASE_URL=http://localhost:5173
CLINIC_TIMEZONE=America/Santiago
BOOKING_AUTO_CONFIRM=false
BOOKING_DURATION_MINUTES=30
BOOKING_MIN_NOTICE_HOURS=2
BOOKING_HORIZON_DAYS=90
EMAIL_DELIVERY=log
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=replace-me
SMTP_PASSWORD=replace-me
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_STARTTLS_REQUIRED=true
SMTP_SSL=false
SMTP_ALLOW_PLAINTEXT=false
CLINIC_EMAIL=contacto@example.cl
WHATSAPP_NUMBER=56900000000
```

- [ ] **Step 5: Pass tests and checkpoint**

Run:

```powershell
.\mvnw.cmd -Dtest=RateLimitServiceTests,AdminBootstrapIntegrationTests test
if (Test-Path .git) {
  git add src/main/java/com/amidog/app/common src/main/java/com/amidog/app/auth .env.example src/test/java/com/amidog/app
  git commit -m "feat: harden account entry points and seed administrator"
}
```

Expected: PASS.

## Task 8: Foundation Cleanup and Full Authentication Verification

**Files:**

- Delete: superseded Phase 1 clinical/ecommerce entities and old global repositories/controllers/services.
- Modify: `src/test/java/com/amidog/app/AppApplicationTests.java`
- Delete: `src/test/java/com/amidog/app/repositories/RepositoryContractTests.java`
- Delete: `src/test/java/com/amidog/app/controllers/CreateReservationIntegrationTests.java`
- Modify: `README.md`

**Interfaces:**

- Produces: a clean compiling account foundation ready for the scheduling plan.

- [ ] **Step 1: Replace the old context test**

```java
class AppApplicationTests extends PostgresIntegrationTest {
    @Test
    void contextLoadsWithValidatedFlywaySchema() {
    }
}
```

- [ ] **Step 2: Remove only superseded files**

Delete the old model graph (`Cart`, `Product`, `Purchase`, `Consultation`, `MedicalRecord`, `Procedure`, and `ProcedureRecord`) immediately. Remove the Phase 1 `Client`, `Pet`, and repository classes only after all imports point to the new `client` package.

Keep the old reservation controller/service/DTO only until the scheduling plan introduces the authenticated replacement; if they cannot compile against the new domain, remove them and document that `/reservation` is intentionally unavailable between plan checkpoints.

- [ ] **Step 3: Run all foundation tests**

Run:

```powershell
.\mvnw.cmd test
```

Expected: all migration, persistence, token, registration, session, recovery, rate-limit, bootstrap, and context tests PASS.

- [ ] **Step 4: Update the README foundation instructions**

Document:

- Java 21, Node 20+, PostgreSQL 17, and Docker for integration tests;
- `.env.example` setup;
- `.\mvnw.cmd test`;
- `.\mvnw.cmd spring-boot:run`;
- email behavior in `dev`, `test`, and `prod`;
- that bookings return in the next implementation plan.

- [ ] **Step 5: Checkpoint**

```powershell
if (Test-Path .git) {
  git add -A
  git commit -m "refactor: complete backend account foundation"
}
```
