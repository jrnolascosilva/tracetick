package com.tracetick.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_name", nullable = false)
    private String orgName;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Customer() {
    }

    private Customer(String orgName, String contactEmail, Instant createdAt) {
        this.orgName = orgName;
        this.contactEmail = contactEmail;
        this.createdAt = createdAt;
    }

    public static Customer create(String orgName, String contactEmail) {
        return new Customer(orgName, contactEmail, Instant.now());
    }

    public Long getId() {
        return id;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
