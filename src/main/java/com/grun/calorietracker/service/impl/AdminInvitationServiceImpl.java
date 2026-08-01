package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class AdminInvitationServiceImpl implements AdminInvitationService {
 private static final SecureRandom RANDOM=new SecureRandom();
 private final AdminInvitationRepository invitationRepository; private final UserRepository userRepository; private final PasswordEncoder passwordEncoder; private final MailDeliveryService mailDeliveryService; private final AdminAuditService adminAuditService;
 @Value("${grun.admin-invitation.expiration-hours:48}") private long expirationHours;
 @Value("${grun.admin-invitation.base-url:http://localhost:5174/?adminInvitation=true}") private String baseUrl;
 @Override @Transactional public AdminInvitationDto create(String ownerEmail,AdminInvitationCreateRequestDto request,String cid){UserEntity owner=requireOwner(ownerEmail);UserRole role=requireRole(request.role());String email=normalize(request.email());userRepository.findByEmail(email).ifPresent(u->{throw new IllegalArgumentException("This email already belongs to an existing account. Use the controlled access grant flow instead.");});invitationRepository.findByEmailIgnoreCaseAndStatus(email,AdminInvitationStatus.PENDING).forEach(this::revokeInternal);return issue(owner,email,role,AdminAuditActionType.ADMIN_INVITATION_CREATE,cid);}
 @Override @Transactional(readOnly=true) public AdminInvitationPageDto list(int page,int size){Page<AdminInvitationEntity> r=invitationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(Math.max(0,page),Math.min(Math.max(1,size),50)));return new AdminInvitationPageDto(r.map(this::dto).getContent(),r.getNumber(),r.getSize(),r.getTotalElements(),r.getTotalPages(),r.isFirst(),r.isLast());}
 @Override @Transactional public AdminInvitationDto resend(String ownerEmail,Long id,String cid){UserEntity owner=requireOwner(ownerEmail);AdminInvitationEntity old=find(id);if(old.getStatus()==AdminInvitationStatus.ACCEPTED)throw new IllegalArgumentException("Accepted invitations cannot be resent.");revokeInternal(old);return issue(owner,old.getEmail(),old.getRole(),AdminAuditActionType.ADMIN_INVITATION_RESEND,cid);}
 @Override @Transactional public void revoke(String ownerEmail,Long id,String cid){requireOwner(ownerEmail);AdminInvitationEntity i=find(id);if(i.getStatus()!=AdminInvitationStatus.PENDING)throw new IllegalArgumentException("Only pending invitations can be revoked.");revokeInternal(i);audit(ownerEmail,AdminAuditActionType.ADMIN_INVITATION_REVOKE,i,cid);}
 @Override @Transactional public AdminInvitationDto inspect(String token){return dto(usable(token));}
 @Override @Transactional public void accept(AdminInvitationAcceptRequestDto request){AdminInvitationEntity i=usable(request.token());UserEntity u=userRepository.findByEmailForUpdate(i.getEmail()).orElseGet(UserEntity::new);if(u.getId()!=null&&u.getRole()!=null&&u.getRole().isAdminRole())throw new IllegalArgumentException("This invitation can no longer be accepted.");u.setEmail(i.getEmail());u.setName(request.name().trim());u.setPassword(passwordEncoder.encode(request.password()));u.setRole(i.getRole());u.setPasswordSet(true);u.setEmailVerified(true);u.setEmailVerifiedAt(Instant.now());u.setAccountEnabled(true);u.setAccountLocked(false);u.setAdminMfaEnabled(false);u.setAdminRoleUpdatedAt(Instant.now());UserEntity saved=userRepository.save(u);i.setStatus(AdminInvitationStatus.ACCEPTED);i.setAcceptedAt(Instant.now());i.setAcceptedUser(saved);invitationRepository.save(i);audit(i.getInvitedBy().getEmail(),AdminAuditActionType.ADMIN_INVITATION_ACCEPT,i,null);}
 private AdminInvitationDto issue(UserEntity owner,String email,UserRole role,AdminAuditActionType action,String cid){String raw=token();AdminInvitationEntity i=new AdminInvitationEntity();i.setEmail(email);i.setRole(role);i.setTokenHash(hash(raw));i.setStatus(AdminInvitationStatus.PENDING);i.setInvitedBy(owner);i.setExpiresAt(Instant.now().plus(Duration.ofHours(Math.max(1,expirationHours))));AdminInvitationEntity saved=invitationRepository.save(i);String link=baseUrl+(baseUrl.contains("?")?"&":"?")+"token="+raw;mailDeliveryService.sendTransactionalEmail(email,"Your GRun admin invitation","You were invited to the GRun admin team as "+role.name()+". Activate your account: "+link,"<p>You were invited to the GRun admin team as <strong>"+role.name()+"</strong>.</p><p><a href=\""+link+"\">Activate account and set password</a></p><p>This single-use link expires in "+expirationHours+" hours.</p>");audit(owner.getEmail(),action,saved,cid);return dto(saved);}
 private AdminInvitationEntity usable(String raw){AdminInvitationEntity i=invitationRepository.findByTokenHashForUpdate(hash(raw)).orElseThrow(()->new IllegalArgumentException("Admin invitation is invalid or has already been used."));if(i.getStatus()!=AdminInvitationStatus.PENDING)throw new IllegalArgumentException("Admin invitation is no longer active.");if(!i.getExpiresAt().isAfter(Instant.now())){i.setStatus(AdminInvitationStatus.EXPIRED);invitationRepository.save(i);throw new IllegalArgumentException("Admin invitation has expired.");}return i;}
 private UserEntity requireOwner(String email){UserEntity o=userRepository.findByEmail(normalize(email)).orElseThrow(()->new IllegalArgumentException("Owner account was not found."));if(o.getRole()!=UserRole.OWNER||!Boolean.TRUE.equals(o.getAccountEnabled())||Boolean.TRUE.equals(o.getAccountLocked()))throw new IllegalArgumentException("Only an active owner can manage admin invitations.");return o;}
 private UserRole requireRole(UserRole role){if(role==null||!role.isAdminRole()||role.isOwner()||role==UserRole.ADMIN)throw new IllegalArgumentException("Only least-privilege admin roles can be invited.");return role;}
 private AdminInvitationEntity find(Long id){return invitationRepository.findById(id).orElseThrow(()->new IllegalArgumentException("Admin invitation was not found."));}
 private void revokeInternal(AdminInvitationEntity i){if(i.getStatus()==AdminInvitationStatus.PENDING){i.setStatus(AdminInvitationStatus.REVOKED);i.setRevokedAt(Instant.now());invitationRepository.save(i);}}
 private void audit(String actor,AdminAuditActionType action,AdminInvitationEntity i,String cid){adminAuditService.record(actor,action,AdminAuditTargetType.ADMIN_INVITATION,i.getId().toString(),null,Map.of("email",i.getEmail(),"role",i.getRole().name(),"status",i.getStatus().name(),"expiresAt",i.getExpiresAt().toString()),cid);}
 private AdminInvitationDto dto(AdminInvitationEntity i){return new AdminInvitationDto(i.getId(),i.getEmail(),i.getRole().name(),i.getStatus().name(),i.getInvitedBy().getEmail(),i.getExpiresAt(),i.getAcceptedAt(),i.getRevokedAt(),i.getCreatedAt());}
 private String normalize(String email){return email.trim().toLowerCase(Locale.ROOT);} private String token(){byte[] b=new byte[32];RANDOM.nextBytes(b);return HexFormat.of().formatHex(b);} private String hash(String raw){if(raw==null||raw.isBlank())throw new IllegalArgumentException("Admin invitation token is required.");try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
