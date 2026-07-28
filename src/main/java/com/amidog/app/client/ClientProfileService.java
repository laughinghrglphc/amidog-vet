package com.amidog.app.client;

import com.amidog.app.auth.AccountPrincipal;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import com.amidog.app.common.api.ConflictException;
import com.amidog.app.common.api.ConflictType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

import static com.amidog.app.client.ClientDtos.ClientProfileResponse;
import static com.amidog.app.client.ClientDtos.ClientProfileUpdateRequest;

@Service
public class ClientProfileService {

    private final CurrentClient currentClient;
    private final ClientRepository clients;
    private final Clock clock;

    public ClientProfileService(CurrentClient currentClient, ClientRepository clients, Clock clock) {
        this.currentClient = currentClient;
        this.clients = clients;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClientProfileResponse get(AccountPrincipal principal) {
        return response(currentClient.require(principal));
    }

    @Transactional
    public ClientProfileResponse update(AccountPrincipal principal, ClientProfileUpdateRequest request) {
        Client client = currentClient.require(principal);
        client.updateProfile(request.name(), request.phone(), clock.instant());
        try {
            return response(clients.saveAndFlush(client));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ConflictException(ConflictType.CLIENT_CONCURRENT_UPDATE);
        }
    }

    private ClientProfileResponse response(Client client) {
        return new ClientProfileResponse(
                client.getId(), client.getName(), client.getPhone(), client.getUser().getEmailNormalized());
    }
}
