package com.ecom.external.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.external.model.FsSyncState;

public interface FsSyncStateRepository extends JpaRepository<FsSyncState, String> {
}
