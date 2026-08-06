package com.amidog.app.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveApiSurfaceContractTests {

    private static final Path ROOT =
            Path.of("").toAbsolutePath().normalize();
    private static final Path PRODUCTION_JAVA =
            ROOT.resolve("src/main/java");

    private static final Pattern RETIRED_CLASS_NAME = Pattern.compile(
            "^(MedicalRecord|Consultation|Procedure(?:Record)?|Cart|Purchase|Product).*\\.java$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SINGULAR_BOOKING_MAPPING = Pattern.compile(
            "@(?:Request|Get|Post|Patch|Put|Delete)Mapping\\s*\\(\\s*\"/reservation(?:\"|/)",
            Pattern.MULTILINE);
    private static final Pattern OBSOLETE_CONFIRMED_BOOLEAN = Pattern.compile(
            "\\bboolean\\s+confirmed\\b|\\b(?:is|get|set)Confirmed\\s*\\(|\\.confirmed\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    @Test
    void retiredBackendClassesAndSingularBookingRouteCannotReturn()
            throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path root : List.of(
                PRODUCTION_JAVA,
                ROOT.resolve("src/test/java"))) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !isAllowedCompatibilityTest(path))
                        .forEach(path ->
                                inspectActiveJava(path, violations));
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void retiredSurfaceExemptionsAreLimitedToTheGuardAndMigrations() {
        assertThat(isAllowedCompatibilityTest(
                ROOT.resolve(
                        "src/test/java/com/amidog/app/contract/ActiveApiSurfaceContractTests.java")))
                .isTrue();
        assertThat(isAllowedCompatibilityTest(
                ROOT.resolve(
                        "src/test/java/com/amidog/app/migration/SchedulingMigrationIntegrationTests.java")))
                .isTrue();
        assertThat(isAllowedCompatibilityTest(
                ROOT.resolve(
                        "src/test/java/com/amidog/app/contract/RetiredDomainFixture.java")))
                .isFalse();
    }

    @Test
    void shippedTextContractsAreStrictUtf8WithoutKnownMojibake()
            throws IOException {
        List<Path> roots = List.of(
                ROOT.resolve("src/main/java"),
                ROOT.resolve("src/test/java"),
                ROOT.resolve("src/main/resources"),
                ROOT.resolve("src/test/resources"),
                ROOT.resolve("docs"));
        Set<String> extensions = Set.of(
                ".java", ".yaml", ".yml", ".properties", ".sql", ".md");
        List<String> violations = new ArrayList<>();

        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> extensions.stream().anyMatch(
                                extension -> path.toString().endsWith(extension)))
                        .forEach(path -> inspectUtf8(path, violations));
            }
        }
        inspectUtf8(ROOT.resolve("README.md"), violations);
        inspectUtf8(ROOT.resolve(".env.example"), violations);

        assertThat(violations).isEmpty();
    }

    private static void inspectActiveJava(
            Path path, List<String> violations) {
        String fileName = path.getFileName().toString();
        if (RETIRED_CLASS_NAME.matcher(fileName).matches()
                || fileName.equals("CreateReservationController.java")) {
            violations.add("retired class: " + ROOT.relativize(path));
        }
        String source = read(path, violations);
        if (source == null) {
            return;
        }
        if (SINGULAR_BOOKING_MAPPING.matcher(source).find()) {
            violations.add("singular booking mapping: "
                    + ROOT.relativize(path));
        }
        if (OBSOLETE_CONFIRMED_BOOLEAN.matcher(source).find()) {
            violations.add("obsolete confirmed boolean: "
                    + ROOT.relativize(path));
        }
    }

    private static boolean isAllowedCompatibilityTest(Path path) {
        String normalized = path.toString().replace('\\', '/');
        return normalized.contains("/src/test/java/com/amidog/app/migration/")
                || normalized.endsWith(
                "/src/test/java/com/amidog/app/contract/"
                        + "ActiveApiSurfaceContractTests.java");
    }

    private static void inspectUtf8(
            Path path, List<String> violations) {
        if (!Files.isRegularFile(path)) {
            violations.add("missing text contract: "
                    + ROOT.relativize(path));
            return;
        }
        String value = read(path, violations);
        if (value == null) {
            return;
        }
        for (String marker : mojibakeMarkers()) {
            if (value.contains(marker)) {
                violations.add("mojibake marker '" + marker + "': "
                        + ROOT.relativize(path));
            }
        }
    }

    private static List<String> mojibakeMarkers() {
        return List.of(
                Character.toString(0xFFFD),
                Character.toString(0x00C3),
                Character.toString(0x00C2),
                new String(new int[]{0x00E2, 0x20AC}, 0, 2),
                new String(new int[]{0x00E2, 0x20AC, 0x2122}, 0, 3),
                new String(new int[]{0x00E2, 0x20AC, 0x0153}, 0, 3),
                new String(new int[]{0x00F0, 0x0178}, 0, 2));
    }

    private static String read(
            Path path, List<String> violations) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(path)))
                    .toString();
        } catch (CharacterCodingException exception) {
            violations.add("invalid UTF-8: " + ROOT.relativize(path));
        } catch (IOException exception) {
            violations.add("unreadable: " + ROOT.relativize(path));
        }
        return null;
    }
}
