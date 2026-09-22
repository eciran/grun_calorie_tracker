package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminMailboxAccountDto;
import com.grun.calorietracker.dto.AdminMailboxAccountRequestDto;
import com.grun.calorietracker.dto.AdminMailboxMessageDto;
import com.grun.calorietracker.dto.AdminMailboxMessageDetailDto;
import com.grun.calorietracker.entity.AdminMailboxAccountEntity;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.AdminMailboxAccountRepository;
import com.grun.calorietracker.security.MailCredentialCipher;
import com.grun.calorietracker.service.AdminMailboxAccountService;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class AdminMailboxAccountServiceImpl implements AdminMailboxAccountService {
    private final AdminMailboxAccountRepository repository;
    private final MailCredentialCipher cipher;

    @Override @Transactional(readOnly=true)
    public List<AdminMailboxAccountDto> list(){return repository.findAllByOrderByEmailAddressAsc().stream().map(this::dto).toList();}

    @Override @Transactional
    public AdminMailboxAccountDto create(AdminMailboxAccountRequestDto request){
        String email=normalizeEmail(request.emailAddress());
        if(repository.findByEmailAddressIgnoreCase(email).isPresent())throw new RequestConflictException("Mailbox account already exists.");
        AdminMailboxAccountEntity entity=new AdminMailboxAccountEntity();
        apply(entity,request,false); return dto(repository.save(entity));
    }

    @Override @Transactional
    public AdminMailboxAccountDto update(long id,AdminMailboxAccountRequestDto request){
        AdminMailboxAccountEntity entity=required(id);
        String email=normalizeEmail(request.emailAddress());
        repository.findByEmailAddressIgnoreCase(email).filter(other->!other.getId().equals(id)).ifPresent(other->{throw new RequestConflictException("Mailbox account already exists.");});
        apply(entity,request,true); return dto(repository.save(entity));
    }

    @Override @Transactional(noRollbackFor=IllegalArgumentException.class)
    public AdminMailboxAccountDto test(long id){
        AdminMailboxAccountEntity entity=required(id); entity.setLastTestedAt(Instant.now());
        if(entity.getPasswordEncrypted()==null||entity.getPasswordEncrypted().isBlank()){
            entity.setLastConnectionStatus("CONFIG_REQUIRED");entity.setLastConnectionError("Mailbox password is not configured.");
            return dto(repository.save(entity));
        }
        try{
            String password=cipher.decrypt(entity.getPasswordEncrypted());
            testImap(entity,password); testSmtp(entity,password);
            entity.setLastConnectionStatus("CONNECTED");entity.setLastConnectionError(null);
        }catch(Exception exception){
            entity.setLastConnectionStatus("FAILED");entity.setLastConnectionError(safeError(exception));
        }
        return dto(repository.save(entity));
    }

    @Override @Transactional(readOnly=true)
    public List<AdminMailboxMessageDto> messages(long id,String requestedFolder,int limit){
        AdminMailboxAccountEntity account=mailboxReady(id);String folderName=safeFolder(requestedFolder);
        try(Store store=openStore(account);Folder folder=store.getFolder(folderName)){
            folder.open(Folder.READ_ONLY);int total=folder.getMessageCount();if(total==0)return List.of();
            Message[] values=folder.getMessages(Math.max(1,total-limit+1),total);List<AdminMailboxMessageDto> result=new ArrayList<>();
            for(int index=values.length-1;index>=0;index--){Message value=values[index];result.add(summary(account,folderName,folder,value));}
            return result;
        }catch(Exception exception){throw new IllegalArgumentException("Mailbox messages could not be loaded: "+safeError(exception));}
    }

    @Override @Transactional(readOnly=true)
    public AdminMailboxMessageDetailDto message(long id,String requestedFolder,long uid){
        AdminMailboxAccountEntity account=mailboxReady(id);String folderName=safeFolder(requestedFolder);
        try(Store store=openStore(account);Folder folder=store.getFolder(folderName)){
            folder.open(Folder.READ_ONLY);if(!(folder instanceof UIDFolder uidFolder))throw new IllegalArgumentException("This mail server does not support stable message identifiers.");
            Message value=uidFolder.getMessageByUID(uid);if(value==null)throw new IllegalArgumentException("Message was not found.");
            List<String> files=new ArrayList<>();collectAttachmentNames(value,files);String body=plainText(value).strip();
            return new AdminMailboxMessageDetailDto(uid,id,account.getEmailAddress(),folderName,subject(value),addresses(value.getFrom()),recipientAddresses(value),instant(value),value.isSet(Flags.Flag.SEEN),!files.isEmpty(),files,limit(body,100000));
        }catch(IllegalArgumentException exception){throw exception;}catch(Exception exception){throw new IllegalArgumentException("Message could not be opened: "+safeError(exception));}
    }

    private AdminMailboxMessageDto summary(AdminMailboxAccountEntity account,String folderName,Folder folder,Message value)throws Exception{
        long uid=folder instanceof UIDFolder uidFolder?uidFolder.getUID(value):value.getMessageNumber();String text=plainText(value).replaceAll("\\s+"," ").trim();
        List<String> files=new ArrayList<>();collectAttachmentNames(value,files);
        return new AdminMailboxMessageDto(uid,account.getId(),account.getEmailAddress(),folderName,subject(value),addresses(value.getFrom()),instant(value),value.isSet(Flags.Flag.SEEN),!files.isEmpty(),limit(text,220));
    }

    private Store openStore(AdminMailboxAccountEntity account)throws Exception{String protocol=account.isImapSsl()?"imaps":"imap";Properties props=mailProperties(protocol,account.isImapSsl());Store store=Session.getInstance(props).getStore(protocol);store.connect(account.getImapHost(),account.getImapPort(),account.getUsername(),cipher.decrypt(account.getPasswordEncrypted()));return store;}
    private AdminMailboxAccountEntity mailboxReady(long id){AdminMailboxAccountEntity value=required(id);if(value.getPasswordEncrypted()==null||value.getPasswordEncrypted().isBlank())throw new IllegalArgumentException("Configure the mailbox password before opening messages.");return value;}
    private String safeFolder(String value){String normalized=value==null?"INBOX":value.trim();Set<String> allowed=Set.of("INBOX","Sent","Drafts","Spam","Trash");return allowed.stream().filter(item->item.equalsIgnoreCase(normalized)).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported mailbox folder."));}
    private String plainText(Part part)throws Exception{if(part.isMimeType("text/plain")){Object content=part.getContent();return content==null?"":content.toString();}if(part.isMimeType("text/html")){Object content=part.getContent();return content==null?"":content.toString().replaceAll("(?is)<(script|style).*?>.*?</\\1>"," ").replaceAll("(?s)<[^>]+>"," ").replace("&nbsp;"," ").replace("&amp;","&");}if(part.isMimeType("multipart/*")){Multipart multipart=(Multipart)part.getContent();String html="";for(int i=0;i<multipart.getCount();i++){BodyPart child=multipart.getBodyPart(i);if(Part.ATTACHMENT.equalsIgnoreCase(child.getDisposition())||child.getFileName()!=null)continue;String text=plainText(child);if(child.isMimeType("text/plain")&&!text.isBlank())return text;if(!text.isBlank())html=text;}return html;}return "";}
    private void collectAttachmentNames(Part part,List<String> names)throws Exception{if(part.getFileName()!=null||Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())){names.add(part.getFileName()==null?"attachment":part.getFileName());return;}if(part.isMimeType("multipart/*")){Multipart multipart=(Multipart)part.getContent();for(int i=0;i<multipart.getCount();i++)collectAttachmentNames(multipart.getBodyPart(i),names);}}
    private List<String> recipientAddresses(Message value)throws Exception{Address[] addresses=value.getAllRecipients();if(addresses==null)return List.of();return Arrays.stream(addresses).map(this::address).toList();}
    private String addresses(Address[] values){return values==null||values.length==0?"Unknown":Arrays.stream(values).map(this::address).reduce((a,b)->a+", "+b).orElse("Unknown");}
    private String address(Address value){if(value instanceof InternetAddress internet){String personal=internet.getPersonal();return personal==null||personal.isBlank()?internet.getAddress():personal+" <"+internet.getAddress()+">";}return value.toString();}
    private String subject(Message value)throws Exception{return value.getSubject()==null||value.getSubject().isBlank()?"(No subject)":value.getSubject();}
    private Instant instant(Message value)throws Exception{java.util.Date date=value.getReceivedDate()!=null?value.getReceivedDate():value.getSentDate();return date==null?null:date.toInstant();}
    private String limit(String value,int max){return value.length()<=max?value:value.substring(0,max)+"…";}

    private void testImap(AdminMailboxAccountEntity account,String password)throws Exception{
        String protocol=account.isImapSsl()?"imaps":"imap";Properties props=mailProperties(protocol,account.isImapSsl());
        try(Store store=Session.getInstance(props).getStore(protocol)){store.connect(account.getImapHost(),account.getImapPort(),account.getUsername(),password);}
    }

    private void testSmtp(AdminMailboxAccountEntity account,String password)throws Exception{
        String protocol=account.isSmtpSsl()?"smtps":"smtp";Properties props=mailProperties(protocol,account.isSmtpSsl());
        props.put("mail."+protocol+".auth","true");props.put("mail."+protocol+".ssl.enable",Boolean.toString(account.isSmtpSsl()));
        try(Transport transport=Session.getInstance(props).getTransport(protocol)){transport.connect(account.getSmtpHost(),account.getSmtpPort(),account.getUsername(),password);}
    }

    private Properties baseProperties(){Properties props=new Properties();props.put("mail.connectiontimeout","10000");props.put("mail.timeout","10000");props.put("mail.writetimeout","10000");return props;}
    private Properties mailProperties(String protocol,boolean ssl){Properties props=baseProperties();props.put("mail."+protocol+".ssl.enable",Boolean.toString(ssl));props.put("mail."+protocol+".connectiontimeout","10000");props.put("mail."+protocol+".timeout","10000");props.put("mail."+protocol+".writetimeout","10000");return props;}

    private void apply(AdminMailboxAccountEntity entity,AdminMailboxAccountRequestDto request,boolean updating){
        String password=request.password()==null?"":request.password().trim();
        if(!password.isEmpty())entity.setPasswordEncrypted(cipher.encrypt(password));
        if(request.enabled()&&(entity.getPasswordEncrypted()==null||entity.getPasswordEncrypted().isBlank()))throw new IllegalArgumentException("Configure the mailbox password before enabling the account.");
        entity.setEmailAddress(normalizeEmail(request.emailAddress()));entity.setDisplayName(trim(request.displayName()));entity.setUsername(request.username().trim());
        entity.setImapHost(request.imapHost().trim());entity.setImapPort(request.imapPort());entity.setImapSsl(request.imapSsl());
        entity.setSmtpHost(request.smtpHost().trim());entity.setSmtpPort(request.smtpPort());entity.setSmtpSsl(request.smtpSsl());entity.setEnabled(request.enabled());
        if(!updating||!password.isEmpty()){entity.setLastConnectionStatus(entity.getPasswordEncrypted()==null?"CONFIG_REQUIRED":"UNTESTED");entity.setLastConnectionError(null);}
    }

    private AdminMailboxAccountEntity required(long id){return repository.findById(id).orElseThrow(()->new IllegalArgumentException("Mailbox account was not found."));}
    private String normalizeEmail(String value){return value.trim().toLowerCase(java.util.Locale.ROOT);}
    private String trim(String value){return value==null||value.isBlank()?null:value.trim();}
    private String safeError(Exception exception){String value=exception.getMessage();if(value==null||value.isBlank())value=exception.getClass().getSimpleName();value=value.replaceAll("(?i)(password|secret|token)=[^\\s,;]+","$1=[REDACTED]");return value.length()>500?value.substring(0,500):value;}
    private AdminMailboxAccountDto dto(AdminMailboxAccountEntity value){return new AdminMailboxAccountDto(value.getId(),value.getEmailAddress(),value.getDisplayName(),value.getUsername(),value.getImapHost(),value.getImapPort(),value.isImapSsl(),value.getSmtpHost(),value.getSmtpPort(),value.isSmtpSsl(),value.isEnabled(),value.getPasswordEncrypted()!=null&&!value.getPasswordEncrypted().isBlank(),value.getLastConnectionStatus(),value.getLastConnectionError(),value.getLastTestedAt(),value.getCreatedAt(),value.getUpdatedAt());}
}
