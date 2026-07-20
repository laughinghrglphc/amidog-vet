package com.amidog.app.catalog;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public final class ServiceCodeNormalizer {

    String normalize(String supplied) {
        if (supplied == null) {
            throw new InvalidServiceCodeException();
        }
        String normalized = Normalizer.normalize(
                        mapNonDecomposingLatin(supplied.trim()),
                        Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (normalized.isBlank() || normalized.length() > 60) {
            throw new InvalidServiceCodeException();
        }
        return normalized;
    }

    /**
     * NFKD covers compatibility forms such as full-width Latin, while these
     * common Latin letters/ligatures do not decompose to ASCII in the JDK.
     */
    private String mapNonDecomposingLatin(String value) {
        StringBuilder mapped = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> mapped.append(switch (codePoint) {
            case 0x00C6 -> "AE";
            case 0x00E6 -> "ae";
            case 0x0152 -> "OE";
            case 0x0153 -> "oe";
            case 0x00D8 -> "O";
            case 0x00F8 -> "o";
            case 0x0141 -> "L";
            case 0x0142 -> "l";
            case 0x00D0, 0x0110 -> "D";
            case 0x00F0, 0x0111 -> "d";
            case 0x00DE -> "TH";
            case 0x00FE -> "th";
            case 0x00DF -> "ss";
            case 0x0126 -> "H";
            case 0x0127 -> "h";
            case 0x014A -> "N";
            case 0x014B -> "n";
            default -> new String(Character.toChars(codePoint));
        }));
        return mapped.toString();
    }

    public static final class InvalidServiceCodeException extends RuntimeException {
    }
}
