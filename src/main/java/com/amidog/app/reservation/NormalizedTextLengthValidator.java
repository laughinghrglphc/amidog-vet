package com.amidog.app.reservation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class NormalizedTextLengthValidator
        implements ConstraintValidator<
                NormalizedTextLength, CharSequence> {

    private int minimum;
    private int maximum;

    @Override
    public void initialize(NormalizedTextLength annotation) {
        minimum = annotation.min();
        maximum = annotation.max();
        if (minimum < 0 || maximum < minimum) {
            throw new IllegalArgumentException(
                    "normalized text length bounds are invalid");
        }
    }

    @Override
    public boolean isValid(
            CharSequence value,
            ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.toString().trim().length();
        return length >= minimum && length <= maximum;
    }
}
