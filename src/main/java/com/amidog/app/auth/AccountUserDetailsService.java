package com.amidog.app.auth;

import com.amidog.app.client.Client;
import com.amidog.app.client.ClientRepository;
import com.amidog.app.config.AmidogProperties;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository users;
    private final ClientRepository clients;
    private final AmidogProperties properties;

    public AccountUserDetailsService(
            UserAccountRepository users,
            ClientRepository clients,
            AmidogProperties properties
    ) {
        this.users = users;
        this.clients = clients;
        this.properties = properties;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String suppliedEmail) throws UsernameNotFoundException {
        String email = normalizeEmail(suppliedEmail);
        UserAccount account = users.findByEmailNormalized(email)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));

        if (account.getAccountType() == AccountType.CLIENT) {
            Client client = clients.findByUserIdAndActiveTrue(account.getId())
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return new AccountPrincipal(
                    account.getId(), client.getId(), account.getEmailNormalized(), client.getName(),
                    account.getAccountType(), account.getPasswordHash(), account.isEnabled(), account.isVerified());
        }

        return new AccountPrincipal(
                account.getId(), null, account.getEmailNormalized(), properties.admin().name(),
                account.getAccountType(), account.getPasswordHash(), account.isEnabled(), account.isVerified());
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return normalized;
    }
}
