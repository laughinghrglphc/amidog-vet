package com.amidog.app.contract;

import com.amidog.app.admin.AdminClientController;
import com.amidog.app.admin.AdminDashboardController;
import com.amidog.app.admin.AdminPetController;
import com.amidog.app.admin.AdminReservationController;
import com.amidog.app.admin.AdminDtos;
import com.amidog.app.admin.PageResponse;
import com.amidog.app.auth.AuthController;
import com.amidog.app.catalog.ServiceCatalogController;
import com.amidog.app.client.ClientController;
import com.amidog.app.contact.ContactController;
import com.amidog.app.notification.AdminNotificationController;
import com.amidog.app.notification.ClientNotificationController;
import com.amidog.app.notification.NotificationDtos;
import com.amidog.app.reservation.ReservationController;
import com.amidog.app.reservation.ReservationLifecycleDtos;
import com.amidog.app.scheduling.AdminAvailabilityController;
import com.amidog.app.scheduling.AvailabilityController;
import com.amidog.app.scheduling.AvailabilityDtos;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDocumentationContractTests {

    private static final Path ROOT =
            Path.of("").toAbsolutePath().normalize();
    private static final Path PRODUCTION_JAVA =
            ROOT.resolve("src/main/java");
    private static final Path API_DOC = ROOT.resolve("docs/api.md");
    private static final Pattern DOCUMENTED_ROUTE = Pattern.compile(
            "(?m)^- `((?:GET|POST|PUT|PATCH|DELETE) /api/v1[^`]*)`");
    private static final Pattern ROUTE_CONTRACT_ROW = Pattern.compile(
            "(?m)^\\| `((?:GET|POST|PUT|PATCH|DELETE) /api/v1[^`]*)`"
                    + " \\| [^|]+ \\| `(\\d{3} [A-Z ]+)` \\| [^|]+ \\|$");
    private static final Pattern SCHEMA_FIELDS_ROW = Pattern.compile(
            "(?m)^\\| `([A-Za-z][A-Za-z0-9]+)` \\| `([^`]*)` \\|$");

    private static final List<Class<?>> CONTROLLERS = List.of(
            AdminClientController.class,
            AdminDashboardController.class,
            AdminPetController.class,
            AdminReservationController.class,
            AuthController.class,
            ServiceCatalogController.class,
            ClientController.class,
            ContactController.class,
            AdminNotificationController.class,
            ClientNotificationController.class,
            ReservationController.class,
            AdminAvailabilityController.class,
            AvailabilityController.class);

    private static final Set<String> CANONICAL_ROUTES = Set.of(
            "GET /api/v1/auth/csrf",
            "POST /api/v1/auth/register",
            "POST /api/v1/auth/verify-email",
            "POST /api/v1/auth/resend-verification",
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/logout",
            "GET /api/v1/auth/me",
            "POST /api/v1/auth/forgot-password",
            "POST /api/v1/auth/reset-password",
            "POST /api/v1/contact",
            "GET /api/v1/services",
            "GET /api/v1/availability",
            "GET /api/v1/me/profile",
            "PATCH /api/v1/me/profile",
            "GET /api/v1/me/pets",
            "POST /api/v1/me/pets",
            "PATCH /api/v1/me/pets/{id}",
            "DELETE /api/v1/me/pets/{id}",
            "GET /api/v1/me/reservations",
            "POST /api/v1/me/reservations",
            "PATCH /api/v1/me/reservations/{id}/cancel",
            "PATCH /api/v1/me/reservations/{id}/reschedule",
            "GET /api/v1/me/notifications",
            "PATCH /api/v1/me/notifications/{id}/read",
            "POST /api/v1/me/notifications/read-all",
            "GET /api/v1/admin/dashboard",
            "GET /api/v1/admin/reservations",
            "GET /api/v1/admin/reservations/{id}",
            "PATCH /api/v1/admin/reservations/{id}/status",
            "PATCH /api/v1/admin/reservations/{id}/reschedule",
            "GET /api/v1/admin/clients",
            "GET /api/v1/admin/clients/{id}",
            "PATCH /api/v1/admin/clients/{id}",
            "GET /api/v1/admin/pets",
            "GET /api/v1/admin/pets/{id}",
            "PATCH /api/v1/admin/pets/{id}",
            "GET /api/v1/admin/services",
            "POST /api/v1/admin/services",
            "PATCH /api/v1/admin/services/{id}",
            "DELETE /api/v1/admin/services/{id}",
            "GET /api/v1/admin/availability/weekly",
            "PUT /api/v1/admin/availability/weekly",
            "GET /api/v1/admin/availability/blocks",
            "POST /api/v1/admin/availability/blocks",
            "DELETE /api/v1/admin/availability/blocks/{id}",
            "GET /api/v1/admin/notifications",
            "PATCH /api/v1/admin/notifications/{id}/read",
            "POST /api/v1/admin/notifications/read-all");

    private static final Map<String, String> SUCCESS_STATUSES =
            Map.ofEntries(
                    Map.entry("GET /api/v1/auth/csrf", "200 OK"),
                    Map.entry("POST /api/v1/auth/register", "202 ACCEPTED"),
                    Map.entry("POST /api/v1/auth/verify-email", "204 NO CONTENT"),
                    Map.entry("POST /api/v1/auth/resend-verification", "202 ACCEPTED"),
                    Map.entry("POST /api/v1/auth/login", "200 OK"),
                    Map.entry("POST /api/v1/auth/logout", "204 NO CONTENT"),
                    Map.entry("GET /api/v1/auth/me", "200 OK"),
                    Map.entry("POST /api/v1/auth/forgot-password", "202 ACCEPTED"),
                    Map.entry("POST /api/v1/auth/reset-password", "204 NO CONTENT"),
                    Map.entry("POST /api/v1/contact", "202 ACCEPTED"),
                    Map.entry("GET /api/v1/services", "200 OK"),
                    Map.entry("GET /api/v1/availability", "200 OK"),
                    Map.entry("GET /api/v1/me/profile", "200 OK"),
                    Map.entry("PATCH /api/v1/me/profile", "200 OK"),
                    Map.entry("GET /api/v1/me/pets", "200 OK"),
                    Map.entry("POST /api/v1/me/pets", "201 CREATED"),
                    Map.entry("PATCH /api/v1/me/pets/{id}", "200 OK"),
                    Map.entry("DELETE /api/v1/me/pets/{id}", "204 NO CONTENT"),
                    Map.entry("GET /api/v1/me/reservations", "200 OK"),
                    Map.entry("POST /api/v1/me/reservations", "201 CREATED"),
                    Map.entry("PATCH /api/v1/me/reservations/{id}/cancel", "200 OK"),
                    Map.entry("PATCH /api/v1/me/reservations/{id}/reschedule", "200 OK"),
                    Map.entry("GET /api/v1/me/notifications", "200 OK"),
                    Map.entry("PATCH /api/v1/me/notifications/{id}/read", "200 OK"),
                    Map.entry("POST /api/v1/me/notifications/read-all", "200 OK"),
                    Map.entry("GET /api/v1/admin/dashboard", "200 OK"),
                    Map.entry("GET /api/v1/admin/reservations", "200 OK"),
                    Map.entry("GET /api/v1/admin/reservations/{id}", "200 OK"),
                    Map.entry("PATCH /api/v1/admin/reservations/{id}/status", "200 OK"),
                    Map.entry("PATCH /api/v1/admin/reservations/{id}/reschedule", "200 OK"),
                    Map.entry("GET /api/v1/admin/clients", "200 OK"),
                    Map.entry("GET /api/v1/admin/clients/{id}", "200 OK"),
                    Map.entry("PATCH /api/v1/admin/clients/{id}", "200 OK"),
                    Map.entry("GET /api/v1/admin/pets", "200 OK"),
                    Map.entry("GET /api/v1/admin/pets/{id}", "200 OK"),
                    Map.entry("PATCH /api/v1/admin/pets/{id}", "200 OK"),
                    Map.entry("GET /api/v1/admin/services", "200 OK"),
                    Map.entry("POST /api/v1/admin/services", "201 CREATED"),
                    Map.entry("PATCH /api/v1/admin/services/{id}", "200 OK"),
                    Map.entry("DELETE /api/v1/admin/services/{id}", "204 NO CONTENT"),
                    Map.entry("GET /api/v1/admin/availability/weekly", "200 OK"),
                    Map.entry("PUT /api/v1/admin/availability/weekly", "200 OK"),
                    Map.entry("GET /api/v1/admin/availability/blocks", "200 OK"),
                    Map.entry("POST /api/v1/admin/availability/blocks", "201 CREATED"),
                    Map.entry("DELETE /api/v1/admin/availability/blocks/{id}", "204 NO CONTENT"),
                    Map.entry("GET /api/v1/admin/notifications", "200 OK"),
                    Map.entry("PATCH /api/v1/admin/notifications/{id}/read", "200 OK"),
                    Map.entry("POST /api/v1/admin/notifications/read-all", "200 OK"));

    private static final Set<String> CREATED_LOCATION_ROUTES = Set.of(
            "POST /api/v1/me/pets",
            "POST /api/v1/me/reservations",
            "POST /api/v1/admin/services",
            "POST /api/v1/admin/availability/blocks");

    private static final Map<String, Class<?>> REPRESENTATIVE_SCHEMAS =
            Map.ofEntries(
                    Map.entry("PageResponse", PageResponse.class),
                    Map.entry("ReservationItemSummary",
                            AdminDtos.ReservationItemSummary.class),
                    Map.entry("AdminReservationSummary",
                            AdminDtos.AdminReservationSummary.class),
                    Map.entry("ReservationEventSummary",
                            AdminDtos.ReservationEventSummary.class),
                    Map.entry("AdminReservationDetail",
                            AdminDtos.AdminReservationDetail.class),
                    Map.entry("AdminClientSummary",
                            AdminDtos.AdminClientSummary.class),
                    Map.entry("ClientPetSummary",
                            AdminDtos.ClientPetSummary.class),
                    Map.entry("ReservationCounts",
                            AdminDtos.ReservationCounts.class),
                    Map.entry("AdminClientDetail",
                            AdminDtos.AdminClientDetail.class),
                    Map.entry("AdminClientUpdateRequest",
                            AdminDtos.AdminClientUpdateRequest.class),
                    Map.entry("AdminPetSummary",
                            AdminDtos.AdminPetSummary.class),
                    Map.entry("AdminPetDetail",
                            AdminDtos.AdminPetDetail.class),
                    Map.entry("AdminPetUpdateRequest",
                            AdminDtos.AdminPetUpdateRequest.class),
                    Map.entry("StatusChangeRequest",
                            ReservationLifecycleDtos.StatusChangeRequest.class),
                    Map.entry("StatusChangeResponse",
                            ReservationLifecycleDtos.StatusChangeResponse.class),
                    Map.entry("WeeklyIntervalRequest",
                            AvailabilityDtos.WeeklyIntervalRequest.class),
                    Map.entry("WeeklyIntervalResponse",
                            AvailabilityDtos.WeeklyIntervalResponse.class),
                    Map.entry("AvailabilityBlockRequest",
                            AvailabilityDtos.AvailabilityBlockRequest.class),
                    Map.entry("AvailabilityBlockResponse",
                            AvailabilityDtos.AvailabilityBlockResponse.class),
                    Map.entry("NotificationResponse",
                            NotificationDtos.NotificationResponse.class),
                    Map.entry("ReadAllResponse",
                            NotificationDtos.ReadAllResponse.class));

    @Test
    void controllerInventoryIsIndependentlyDiscoveredFromProduction()
            throws IOException {
        assertThat(discoverProductionControllers())
                .containsExactlyInAnyOrderElementsOf(CONTROLLERS);
    }

    @Test
    void canonicalRouteListMatchesEveryCurrentControllerAndSecurityRoute()
            throws IOException {
        Set<String> actual = controllerRoutes();
        actual.add("POST /api/v1/auth/login");
        actual.add("POST /api/v1/auth/logout");

        assertThat(actual).containsExactlyInAnyOrderElementsOf(
                CANONICAL_ROUTES);
    }

    @Test
    void apiReferenceDocumentsEveryAndOnlyImplementedRoute()
            throws IOException {
        String document = apiDocument();
        Set<String> documented = new LinkedHashSet<>();
        Matcher matcher = DOCUMENTED_ROUTE.matcher(document);
        while (matcher.find()) {
            documented.add(matcher.group(1));
        }

        assertThat(documented)
                .containsExactlyInAnyOrderElementsOf(CANONICAL_ROUTES);
        assertThat(document)
                .doesNotContain(
                        "POST /reservation",
                        "/api/v1/messages",
                        "/api/v1/settings",
                        "/api/v1/auth/google",
                        "/api/v1/medical",
                        "/api/v1/products",
                        "/api/v1/purchases",
                        "/api/v1/carts");
    }

    @Test
    void apiReferenceDefinesTheExactSuccessStatusForEveryRoute()
            throws IOException {
        Map<String, String> documented = new LinkedHashMap<>();
        Matcher matcher = ROUTE_CONTRACT_ROW.matcher(apiDocument());
        while (matcher.find()) {
            assertThat(documented.put(matcher.group(1), matcher.group(2)))
                    .as("duplicate route contract for %s", matcher.group(1))
                    .isNull();
        }

        assertThat(documented).containsExactlyInAnyOrderEntriesOf(
                SUCCESS_STATUSES);
    }

    @Test
    void createdCollectionSummaryMatchesEveryLocationRoute()
            throws IOException {
        Set<String> documentedCreatedRoutes = new LinkedHashSet<>();
        Matcher matcher = ROUTE_CONTRACT_ROW.matcher(apiDocument());
        while (matcher.find()) {
            if ("201 CREATED".equals(matcher.group(2))) {
                documentedCreatedRoutes.add(matcher.group(1));
            }
        }

        assertThat(documentedCreatedRoutes)
                .containsExactlyInAnyOrderElementsOf(
                        CREATED_LOCATION_ROUTES);
        assertThat(apiDocument().replaceAll("\\s+", " ")).contains(
                "`Location` is also returned by the four "
                        + "`201 CREATED` collection routes.");
    }

    @Test
    void apiReferenceSchemasTrackRepresentativeDtoFields()
            throws IOException {
        Map<String, String> documented = new LinkedHashMap<>();
        Matcher matcher = SCHEMA_FIELDS_ROW.matcher(apiDocument());
        while (matcher.find()) {
            documented.put(matcher.group(1), matcher.group(2));
        }

        REPRESENTATIVE_SCHEMAS.forEach((name, type) -> {
            String expected = Arrays.stream(type.getRecordComponents())
                    .map(component -> component.getName())
                    .collect(Collectors.joining(","));
            assertThat(documented.get(name))
                    .as("exact fields for %s", name)
                    .isEqualTo(expected);
        });
    }

    @Test
    void docsAndReadmePreserveClosedBookingAndConfigurationContracts()
            throws IOException {
        String document = apiDocument();
        String readme = Files.readString(ROOT.resolve("README.md"));

        assertThat(document)
                .contains(
                        "\"code\": \"SLOT_ALREADY_BOOKED\"",
                        "\"message\": \"Ese horario acaba de ser reservado. Elige otro bloque disponible.\"",
                        "PENDING",
                        "CONFIRMED",
                        "CANCELLED",
                        "COMPLETED",
                        "NO_SHOW",
                        "America/Santiago",
                        "1900-01-01",
                        "2100-12-31");
        assertThat(readme)
                .doesNotContain(
                        "Temporary booking checkpoint",
                        "reservation creation is unavailable",
                        "scheduled for the next implementation phase")
                .contains(
                        "docs/api.md",
                        "BOOKING_AUTO_CONFIRM=false",
                        "CLINIC_TIMEZONE=America/Santiago",
                        "NOTIFICATION_REMINDER_WINDOW_START_HOURS",
                        "TOKEN_CLEANUP_CRON");
    }

    @Test
    void exampleEnvironmentContainsEverySupportedReminderDefault()
            throws IOException {
        String example = Files.readString(ROOT.resolve(".env.example"));

        assertThat(example).contains(
                "NOTIFICATION_REMINDERS_ENABLED=true",
                "NOTIFICATION_REMINDER_WINDOW_START_HOURS=23",
                "NOTIFICATION_REMINDER_WINDOW_END_HOURS=25",
                "NOTIFICATION_REMINDER_BATCH_SIZE=100",
                "NOTIFICATION_REMINDER_CRON=0 5 * * * *",
                "TOKEN_CLEANUP_CRON=0 30 3 * * *");
    }

    private static String apiDocument() throws IOException {
        assertThat(API_DOC)
                .as("reader-ready API reference")
                .exists();
        return Files.readString(API_DOC);
    }

    private static Set<String> controllerRoutes() throws IOException {
        Set<String> routes = new LinkedHashSet<>();
        for (Class<?> controller : discoverProductionControllers()) {
            RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(
                    controller, RequestMapping.class);
            String basePath = singlePath(base, controller.getName());
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping =
                        AnnotatedElementUtils.findMergedAnnotation(
                                method, RequestMapping.class);
                if (mapping == null || mapping.method().length == 0) {
                    continue;
                }
                String methodPath = singlePath(
                        mapping,
                        controller.getSimpleName() + "#" + method.getName());
                Arrays.stream(mapping.method()).forEach(httpMethod ->
                        routes.add(httpMethod.name()
                                + " " + basePath + methodPath));
            }
        }
        return routes;
    }

    private static Set<Class<?>> discoverProductionControllers()
            throws IOException {
        Set<Class<?>> controllers = new LinkedHashSet<>();
        try (var files = Files.walk(PRODUCTION_JAVA)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(ApiDocumentationContractTests::loadProductionClass)
                    .filter(type -> AnnotatedElementUtils.hasAnnotation(
                            type, RestController.class))
                    .forEach(controllers::add);
        }
        return controllers;
    }

    private static Class<?> loadProductionClass(Path source) {
        String className = PRODUCTION_JAVA.relativize(source)
                .toString()
                .replace('\\', '.')
                .replace('/', '.')
                .replaceFirst("\\.java$", "");
        try {
            return Class.forName(
                    className,
                    false,
                    ApiDocumentationContractTests.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError(
                    "Production source did not compile to " + className,
                    exception);
        }
    }

    private static String singlePath(
            RequestMapping mapping, String owner) {
        assertThat(mapping)
                .as("mapping for %s", owner)
                .isNotNull();
        String[] paths = mapping.path().length == 0
                ? mapping.value()
                : mapping.path();
        assertThat(paths)
                .as("single route path for %s", owner)
                .hasSizeLessThanOrEqualTo(1);
        return paths.length == 0 ? "" : paths[0];
    }
}
