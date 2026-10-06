package com.ecom.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecom.model.LoginAnnouncement;

public interface LoginAnnouncementRepository extends JpaRepository<LoginAnnouncement, Integer> {
}
