package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;

@Entity
@Table(name = "admin_mailbox_accounts")
@Data
public class AdminMailboxAccountEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="email_address",nullable=false,unique=true,length=320) private String emailAddress;
    @Column(name="display_name",length=160) private String displayName;
    @Column(nullable=false,length=320) private String username;
    @Column(name="password_encrypted",columnDefinition="TEXT") private String passwordEncrypted;
    @Column(name="imap_host",nullable=false) private String imapHost;
    @Column(name="imap_port",nullable=false) private int imapPort=993;
    @Column(name="imap_ssl",nullable=false) private boolean imapSsl=true;
    @Column(name="smtp_host",nullable=false) private String smtpHost;
    @Column(name="smtp_port",nullable=false) private int smtpPort=465;
    @Column(name="smtp_ssl",nullable=false) private boolean smtpSsl=true;
    @Column(nullable=false) private boolean enabled;
    @Column(name="last_connection_status",nullable=false,length=32) private String lastConnectionStatus="CONFIG_REQUIRED";
    @Column(name="last_connection_error",length=500) private String lastConnectionError;
    @Column(name="last_tested_at") private Instant lastTestedAt;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    @PrePersist void create(){Instant now=Instant.now();if(createdAt==null)createdAt=now;updatedAt=now;}
    @PreUpdate void update(){updatedAt=Instant.now();}
}
