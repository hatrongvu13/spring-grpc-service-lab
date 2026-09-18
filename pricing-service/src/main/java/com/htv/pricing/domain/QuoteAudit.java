package com.htv.pricing.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "quote_audit")
public class QuoteAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public double weightKg;
    public long amountMinor;
    public String currency;
    public Instant createdAt;

    protected QuoteAudit() {
    }

    public QuoteAudit(double w, long a) {
        weightKg = w;
        amountMinor = a;
        currency = "VND";
        createdAt = Instant.now();
    }
}