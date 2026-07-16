package com.amidog.app.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "user_external_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static UserExternalIdentity create(
            UserAccount user, String provider, String providerSubject, Instant createdAt
    ) {
        return new UserExternalIdentity(user, provider, providerSubject, createdAt);
    }

    private UserExternalIdentity(UserAccount user, String provider, String providerSubject, Instant createdAt) {
        this.user = user;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.createdAt = createdAt;
    }
}
