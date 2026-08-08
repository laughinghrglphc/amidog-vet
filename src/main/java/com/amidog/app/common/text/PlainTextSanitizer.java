package com.amidog.app.common.text;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

@Component
public class PlainTextSanitizer {

    public String clean(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        return normalized
                .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }
}
