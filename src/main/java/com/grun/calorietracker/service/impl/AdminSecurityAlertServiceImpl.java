package com.grun.calorietracker.service.impl;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.repository.NotificationRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminSecurityAlertService;
import com.grun.calorietracker.service.PushDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
@Service @RequiredArgsConstructor
public class AdminSecurityAlertServiceImpl implements AdminSecurityAlertService {
 private final NotificationRepository notifications; private final UserRepository users; private final AdminAuditService audit;
 @Autowired(required=false) private PushDeliveryService pushDeliveryService;
 @Value("${grun.security.owner-bootstrap.primary-email:}") private String primaryOwnerEmail;
 @Override @Transactional(propagation=Propagation.REQUIRES_NEW) public void mfaFailure(UserEntity user){audit.record(user.getEmail(),AdminAuditActionType.ADMIN_MFA_FAILURE,AdminAuditTargetType.ADMIN_ACCOUNT,user.getId().toString(),null,Map.of("result","REJECTED"),null);notifyOwners(user,"MFA verification failed","An invalid authenticator or recovery code was submitted.","WARNING");}
 @Override @Transactional(propagation=Propagation.REQUIRES_NEW) public void recoveryCodeUsed(UserEntity user){audit.record(user.getEmail(),AdminAuditActionType.ADMIN_MFA_RECOVERY_CODE_USE,AdminAuditTargetType.ADMIN_ACCOUNT,user.getId().toString(),null,Map.of("recoveryCodeUsed",true),null);notifyOwners(user,"Owner recovery code used","A single-use MFA recovery code was consumed. Review active sessions and remaining codes.","CRITICAL");}
 private void notifyOwners(UserEntity user,String title,String message,String severity){save(user,title,message,severity);if(primaryOwnerEmail!=null&&!primaryOwnerEmail.isBlank()&&!primaryOwnerEmail.equalsIgnoreCase(user.getEmail()))users.findByEmail(primaryOwnerEmail).ifPresent(primary->save(primary,title,message,severity));}
 private void save(UserEntity recipient,String title,String message,String severity){NotificationEntity n=new NotificationEntity();n.setUser(recipient);n.setTitle(title);n.setMessage(message);n.setType("admin_security_alert");n.setSeverity(severity);n.setSource("ADMIN_SECURITY");n.setTargetType("ADMIN_ACCOUNT");n.setTargetId(recipient.getId().toString());n.setTargetRoute("admins");n.setVisibleInApp(true);n.setIsRead(false);n.setCreatedAt(LocalDateTime.now());NotificationEntity saved=notifications.save(n);if(pushDeliveryService!=null)pushDeliveryService.deliver(saved);}
}
