package com.stockflow.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import com.stockflow.security.SecurityProperties.EdgeSettings;

class EdgeSettingsTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    @Test
    void emptySecretMeansTheEdgeCheckIsOff() {
        EdgeSettings settings = new EdgeSettings("");
        assertThat(VALIDATOR.validate(settings)).isEmpty();
        assertThat(settings.enabled()).isFalse();
        assertThat(new EdgeSettings(null).enabled()).isFalse();
    }

    @Test
    void secretShorterThan32CharactersIsRefused() {
        assertThat(VALIDATOR.validate(new EdgeSettings("a".repeat(31)))).isNotEmpty();
    }

    @Test
    void secretOf32CharactersTurnsTheEdgeCheckOn() {
        EdgeSettings settings = new EdgeSettings("a".repeat(32));
        assertThat(VALIDATOR.validate(settings)).isEmpty();
        assertThat(settings.enabled()).isTrue();
    }

    @Test
    void toStringNeverShowsTheSecret() {
        assertThat(new EdgeSettings("super-secret-value-0123456789abcdef").toString())
                .doesNotContain("super-secret-value");
    }
}
