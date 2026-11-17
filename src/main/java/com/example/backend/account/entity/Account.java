package com.example.backend.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * account 테이블 (create_table.sql 1. ACCOUNT)
 */
@Entity
@Table(name = "account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uid", nullable = false, updatable = false)
    private UUID uid;

    @Column(name = "login_id", nullable = false, unique = true, length = 100, updatable = false)
    private String loginId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "role_level", nullable = false, updatable = false)
    private Short roleLevel;

    @Column(name = "organization")
    private String organization;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "phone", length = 20)
    private String phone;

    public Account(String loginId, String passwordHash, RoleLevel roleLevel,
                   String name, String organization, String phone) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.roleLevel = roleLevel.getLevel();
        this.name = name;
        this.organization = organization;
        this.phone = phone;
    }

    public RoleLevel getRole() {
        return RoleLevel.fromLevel(roleLevel);
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeOrganization(String organization) {
        this.organization = organization;
    }

    public void changePhone(String phone) {
        this.phone = phone;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
