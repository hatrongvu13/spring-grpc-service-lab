package com.htv.pricing.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteAuditRepository extends JpaRepository<QuoteAudit, Long> {
}