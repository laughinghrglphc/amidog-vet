package com.amidog.app.client;

import com.amidog.app.auth.AccountPrincipal;
import com.amidog.app.common.api.NotFoundException;
import org.springframework.stereotype.Component;

@Component
public class CurrentClient {

    static final String NOT_FOUND = "No se encontró el perfil de cliente.";

    private final ClientRepository clients;

    public CurrentClient(ClientRepository clients) {
        this.clients = clients;
    }

    /**
     * Resolves ownership from the authenticated user id. The client id contained
     * in the session snapshot is deliberately not trusted as authorization input.
     */
    public Client require(AccountPrincipal principal) {
        if (principal == null) {
            throw new NotFoundException(NOT_FOUND);
        }
        return clients.findByUserIdAndActiveTrue(principal.getUserId())
                .orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }
}
