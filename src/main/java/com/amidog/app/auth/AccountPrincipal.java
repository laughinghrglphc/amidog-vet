package com.amidog.app.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.CredentialsContainer;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Session-safe account snapshot.  It deliberately contains no managed JPA entity or proxy.
 */
public final class AccountPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final Long clientId;
    private final String email;
    private final String displayName;
    private final AccountType accountType;
    /**
     * Required only by the authentication provider's initial password comparison.
     * It is intentionally excluded from Java serialization so Spring Session never
     * writes a password hash to spring_session_attributes.
     */
    private transient String passwordHash;
    private final boolean enabled;
    private final boolean verified;
    private final List<String> authorityNames;

    public AccountPrincipal(
            Long userId,
            Long clientId,
            String email,
            String displayName,
            AccountType accountType,
            String passwordHash,
            boolean enabled,
            boolean verified
    ) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.clientId = clientId;
        this.email = Objects.requireNonNull(email, "email");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.accountType = Objects.requireNonNull(accountType, "accountType");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.enabled = enabled;
        this.verified = verified;
        this.authorityNames = List.of("ROLE_" + accountType.name());
    }

    public Long getUserId() {
        return userId;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorityNames.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    @JsonIgnore
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled && verified;
    }

    @Override
    public String toString() {
        return "AccountPrincipal[userId=" + userId + ", email=" + email
                + ", accountType=" + accountType + "]";
    }
}
