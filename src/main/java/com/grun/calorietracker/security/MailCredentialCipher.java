package com.grun.calorietracker.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class MailCredentialCipher {
    private final SecretKeySpec key; private final SecureRandom random=new SecureRandom();
    public MailCredentialCipher(@Value("${grun.security.mail-credential-encryption-key:${jwt.secret}}") String material){
        try{key=new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)),"AES");}
        catch(Exception exception){throw new IllegalStateException("Mail credential encryption could not be initialized.",exception);}
    }
    public String encrypt(String value){try{byte[] iv=new byte[12];random.nextBytes(iv);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));byte[] result=new byte[iv.length+encrypted.length];System.arraycopy(iv,0,result,0,iv.length);System.arraycopy(encrypted,0,result,iv.length,encrypted.length);return Base64.getEncoder().encodeToString(result);}catch(Exception exception){throw new IllegalStateException("Mail credential could not be encrypted.",exception);}}
    public String decrypt(String value){try{byte[] all=Base64.getDecoder().decode(value);byte[] iv=java.util.Arrays.copyOfRange(all,0,12);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));return new String(cipher.doFinal(java.util.Arrays.copyOfRange(all,12,all.length)),StandardCharsets.UTF_8);}catch(Exception exception){throw new IllegalStateException("Mail credential could not be decrypted.",exception);}}
}
