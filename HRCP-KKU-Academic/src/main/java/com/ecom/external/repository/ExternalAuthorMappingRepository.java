package com.ecom.external.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ecom.external.model.ExternalAuthorMapping;

@Repository
public interface ExternalAuthorMappingRepository extends JpaRepository<ExternalAuthorMapping, Long> {

    List<ExternalAuthorMapping> findByFsUserId(Long fsUserId);

    List<ExternalAuthorMapping> findByProvider(String provider);

    Optional<ExternalAuthorMapping> findByFsUserIdAndProviderAndExternalPid(Long fsUserId, String provider, String externalPid);

    List<ExternalAuthorMapping> findByFsUserIdAndProvider(Long fsUserId, String provider);

    boolean existsByFsUserIdAndProviderAndExternalPid(Long fsUserId, String provider, String externalPid);
}
