package com.amidog.app.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserExternalIdentityRepository extends JpaRepository<UserExternalIdentity, Long> {

    Optional<UserExternalIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);
}
