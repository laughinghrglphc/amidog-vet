package com.amidog.app.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceCodeNormalizerTests {

    private final ServiceCodeNormalizer normalizer = new ServiceCodeNormalizer();

    @Test
    void producesDeterministicLowercaseAsciiKebabCase() {
        assertThat(normalizer.normalize("  Atención   DENTÁL / Felina  "))
                .isEqualTo("atencion-dental-felina");
        assertThat(normalizer.normalize("Consulta___General"))
                .isEqualTo("consulta-general");
        assertThat(normalizer.normalize("Ｃｏｎｓｕｌｔａ Ｇｅｎｅｒａｌ"))
                .isEqualTo("consulta-general");
        assertThat(normalizer.normalize("Œsófago Æreo Łódź Straße"))
                .isEqualTo("oesofago-aereo-lodz-strasse");
    }

    @Test
    void rejectsBlankAndNormalizedValuesLongerThanDatabaseColumn() {
        assertThatThrownBy(() -> normalizer.normalize("---"))
                .isInstanceOf(ServiceCodeNormalizer.InvalidServiceCodeException.class);
        assertThatThrownBy(() -> normalizer.normalize("a".repeat(61)))
                .isInstanceOf(ServiceCodeNormalizer.InvalidServiceCodeException.class);
        assertThatThrownBy(() -> normalizer.normalize("猫の診察"))
                .isInstanceOf(ServiceCodeNormalizer.InvalidServiceCodeException.class);
        assertThatThrownBy(() -> normalizer.normalize("🐾🩺"))
                .isInstanceOf(ServiceCodeNormalizer.InvalidServiceCodeException.class);
    }
}
