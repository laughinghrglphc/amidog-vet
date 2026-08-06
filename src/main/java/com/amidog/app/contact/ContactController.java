package com.amidog.app.contact;

import com.amidog.app.common.api.ApiErrorResponse;
import com.amidog.app.contact.ContactDtos.ContactRequest;
import com.amidog.app.contact.ContactDtos.ContactResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contact")
public class ContactController {

    private static final String ACCEPTED_MESSAGE =
            "Tu consulta fue enviada.";
    private static final String DELIVERY_UNAVAILABLE_MESSAGE =
            "No pudimos enviar tu consulta. Inténtalo nuevamente o usa WhatsApp.";
    private static final String INVALID_REQUEST_MESSAGE =
            "La solicitud contiene datos inválidos.";

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ContactResponse submit(
            @Valid @RequestBody ContactRequest request,
            HttpServletRequest servletRequest
    ) {
        contactService.submit(request, servletRequest.getRemoteAddr());
        return new ContactResponse(ACCEPTED_MESSAGE);
    }

    @ExceptionHandler(MailException.class)
    ResponseEntity<ApiErrorResponse> handleEmailDeliveryUnavailable() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of(
                        "EMAIL_DELIVERY_UNAVAILABLE",
                        DELIVERY_UNAVAILABLE_MESSAGE));
    }

    @ExceptionHandler(ContactService.CleanedContactValidationException.class)
    ResponseEntity<ApiErrorResponse> handleCleanedValidation(
            ContactService.CleanedContactValidationException exception
    ) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                "VALIDATION_ERROR",
                INVALID_REQUEST_MESSAGE,
                exception.errors()));
    }
}
