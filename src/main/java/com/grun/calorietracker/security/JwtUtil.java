package com.grun.calorietracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.time.Instant;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationTime;

    private Key signKey;

    @org.springframework.beans.factory.annotation.Autowired
    private com.grun.calorietracker.repository.UserRepository users;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.signKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username) {
        return generateUserToken(requireUser(username));
    }

    public String generateUserToken(com.grun.calorietracker.entity.UserEntity user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("authVersion", user.getAuthTokenVersion())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateAdminSessionToken(String username, String sessionId, Instant absoluteExpiry) {
        return generateAdminSessionToken(username, sessionId, absoluteExpiry, requireUser(username).getAuthTokenVersion());
    }

    public String generateAdminSessionToken(String username, String sessionId, Instant absoluteExpiry, long version) {
        long expiresAt = Math.min(System.currentTimeMillis() + expirationTime, absoluteExpiry.toEpochMilli());
        return Jwts.builder().setSubject(username).claim("adminSessionId", sessionId)
                .claim("authVersion", version)
                .setIssuedAt(new Date()).setExpiration(new Date(expiresAt))
                .signWith(signKey, SignatureAlgorithm.HS256).compact();
    }

    public String extractAdminSessionId(String token) {
        return extractAllClaims(token).get("adminSessionId", String.class);
    }

    public String generateAdminMfaEnrollmentToken(String username) {
        return generateAdminMfaEnrollmentTokenForUser(requireUser(username));
    }

    public String generateAdminMfaEnrollmentTokenForUser(com.grun.calorietracker.entity.UserEntity user) {
        return Jwts.builder().setSubject(user.getEmail()).claim("purpose", "ADMIN_MFA_ENROLLMENT")
                .claim("authVersion", user.getAuthTokenVersion())
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(signKey, SignatureAlgorithm.HS256).compact();
    }

    public boolean isAdminMfaEnrollmentToken(String token) {
        return "ADMIN_MFA_ENROLLMENT".equals(extractAllClaims(token).get("purpose", String.class));
    }

    public String generateAdminReauthenticationToken(String username) {
        return generateAdminReauthenticationToken(username, com.grun.calorietracker.enums.AdminReauthenticationPurpose.APPROVAL_DECISION);
    }

    public String generateAdminReauthenticationToken(String username, com.grun.calorietracker.enums.AdminReauthenticationPurpose purpose) {
        return generateAdminReauthenticationTokenForUser(requireUser(username), purpose);
    }

    public String generateAdminReauthenticationTokenForUser(com.grun.calorietracker.entity.UserEntity user, com.grun.calorietracker.enums.AdminReauthenticationPurpose purpose) {
        long expiresIn = 300_000L;
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("purpose", "ADMIN_REAUTH:" + purpose.name())
                .claim("authVersion", user.getAuthTokenVersion())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiresIn))
                .signWith(signKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isAdminReauthenticationTokenValid(String token, String username) {
        return isAdminReauthenticationTokenValid(token, username, com.grun.calorietracker.enums.AdminReauthenticationPurpose.APPROVAL_DECISION);
    }

    public boolean isAdminReauthenticationTokenValid(String token, String username, com.grun.calorietracker.enums.AdminReauthenticationPurpose purpose) {
        Claims claims = extractAllClaims(token);
        return username.equals(claims.getSubject())
                && ("ADMIN_REAUTH:" + purpose.name()).equals(claims.get("purpose", String.class))
                && hasCurrentVersion(claims, username)
                && claims.getExpiration().after(new Date());
    }
    public long getExpirationSeconds() {
        return expirationTime / 1000;
    }

    public boolean isTokenValid(String token, String username) {
        Claims claims = extractAllClaims(token);
        return username.equals(claims.getSubject()) && claims.getExpiration().after(new Date())
                && hasCurrentVersion(claims, username);
    }

    private com.grun.calorietracker.entity.UserEntity requireUser(String username) {
        return users.findByEmail(username).orElseThrow(() ->
                new org.springframework.security.core.userdetails.UsernameNotFoundException("Invalid credentials"));
    }

    private boolean hasCurrentVersion(Claims claims, String username) {
        Object claim = claims.get("authVersion");
        // Legacy JWTs remain usable only until the account's first global revocation.
        if (claim != null && !(claim instanceof Integer || claim instanceof Long)) return false;
        long version = claim == null ? 0 : ((Number) claim).longValue();
        return users.findByEmail(username)
                .flatMap(user -> users.findAuthTokenVersionById(user.getId()))
                .filter(current -> current == version).isPresent();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
