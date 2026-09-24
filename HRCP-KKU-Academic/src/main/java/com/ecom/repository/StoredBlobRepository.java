package com.ecom.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.model.StoredBlob;

public interface StoredBlobRepository extends JpaRepository<StoredBlob, String> {
}
