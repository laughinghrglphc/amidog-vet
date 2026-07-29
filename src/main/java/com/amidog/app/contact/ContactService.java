package com.amidog.app.contact;

import com.amidog.app.common.api.TooManyRequestsException;
import com.amidog.app.common.security.RateLimitService;
import com.amidog.app.common.text.PlainTextSanitizer;
import com.amidog.app.config.AmidogProperties;
import com.amidog.app.contact.ContactDtos.ContactRequest;
import com.amidog.app.email.EmailMessage;
import com.amidog.app.email.EmailSender;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ContactService {

    static final int CONTACT_LIMIT = 5;
    static final Duration CONTACT_WINDOW = Duration.ofHours(1);

    private static final String CONTACT_SUBJECT =
            "Nueva consulta desde el sitio web de AmiDog";
    private static final String ACKNOWLEDGEMENT_SUBJECT =
            "Recibimos tu consulta en AmiDog";

    private final EmailSender emailSender;
    private final AmidogProperties properties;
    private final RateLimitService rateLimits;
    private final PlainTextSanitizer sanitizer;
    private final Validator validator;

    public ContactService(
            EmailSender emailSender,
            AmidogProperties properties,
            RateLimitService rateLimits,
            PlainTextSanitizer sanitizer,
            Validator validator
    ) {
        this.emailSender = emailSender;
        this.properties = properties;
        this.rateLimits = rateLimits;
        this.sanitizer = sanitizer;
        this.validator = validator;
    }

    public void submit(ContactRequest request, String remoteAddress) {
        ContactRequest cleaned = clean(request);
        validate(cleaned);

        String address = remoteAddress == null || remoteAddress.isBlank()
                ? "unknown"
                : remoteAddress;
        if (!rateLimits.tryAcquire(
                "contact:" + address,
                CONTACT_LIMIT,
                CONTACT_WINDOW)) {
            throw new TooManyRequestsException();
        }

        if (cleaned.website() != null && !cleaned.website().isBlank()) {
            return;
        }

        emailSender.send(new EmailMessage(
                properties.contact().clinicEmail(),
                CONTACT_SUBJECT,
                clinicText(cleaned),
                cleaned.email()));
        emailSender.send(new EmailMessage(
                cleaned.email(),
                ACKNOWLEDGEMENT_SUBJECT,
                acknowledgementText(cleaned),
                null));
    }

    private ContactRequest clean(ContactRequest request) {
        return new ContactRequest(
                sanitizer.clean(request.name()),
                sanitizer.clean(request.email()),
                sanitizer.clean(request.message()),
                request.website() == null
                        ? null
                        : sanitizer.clean(request.website()));
    }

    private void validate(ContactRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        validator.validate(request).stream()
                .sorted((left, right) -> property(left)
                        .compareTo(property(right)))
                .forEach(violation -> errors.putIfAbsent(
                        property(violation),
                        violation.getMessage()));
        if (!errors.isEmpty()) {
            throw new CleanedContactValidationException(errors);
        }
    }

    private String clinicText(ContactRequest request) {
        return """
                Nueva consulta desde el sitio web de AmiDog

                Nombre: %s
                Correo: %s

                Mensaje:
                %s
                """.formatted(
                request.name(),
                request.email(),
                request.message());
    }

    private String acknowledgementText(ContactRequest request) {
        return """
                Hola %s:

                Recibimos tu consulta y nos pondremos en contacto contigo a la brevedad.

                Equipo AmiDog
                """.formatted(request.name());
    }

    private static String property(ConstraintViolation<ContactRequest> violation) {
        return violation.getPropertyPath().toString();
    }

    static final class CleanedContactValidationException
            extends RuntimeException {
        private final Map<String, String> errors;

        CleanedContactValidationException(Map<String, String> errors) {
            this.errors = Map.copyOf(errors);
        }

        Map<String, String> errors() {
            return errors;
        }
    }
}
