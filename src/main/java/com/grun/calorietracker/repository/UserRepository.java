package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {
    default Optional<UserEntity> findByEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return singleEmailMatch(findCanonicalEmailMatches(
                com.grun.calorietracker.entity.EmailAddress.canonical(email), org.springframework.data.domain.PageRequest.of(0, 2)));
    }

    @Query("select u from UserEntity u where lower(trim(u.email)) = :email order by u.id")
    List<UserEntity> findCanonicalEmailMatches(@Param("email") String email, Pageable pageable);

    default Optional<UserEntity> findByEmailForUpdate(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return singleEmailMatch(findCanonicalEmailMatchesForUpdate(
                com.grun.calorietracker.entity.EmailAddress.canonical(email), org.springframework.data.domain.PageRequest.of(0, 2)));
    }

    private static Optional<UserEntity> singleEmailMatch(List<UserEntity> matches) {
        if (matches.size() > 1) {
            throw new com.grun.calorietracker.exception.RequestConflictException(
                    "Account email conflict. Please contact support.");
        }
        return matches.stream().findFirst();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where lower(trim(u.email)) = :email order by u.id")
    List<UserEntity> findCanonicalEmailMatchesForUpdate(@Param("email") String email, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") Long id);

    long countByRole(UserRole role);

    List<UserEntity> findByRole(UserRole role);

    Page<UserEntity> findByRoleIn(Collection<UserRole> roles, Pageable pageable);

    List<UserEntity> findByRoleIn(Collection<UserRole> roles);

    long countByRoleIn(Collection<UserRole> roles);

    long countByRoleAndAccountEnabledTrue(UserRole role);

    long countByCreatedAtIsNull();

    @Query("""
            select count(user)
            from UserEntity user
            where user.createdAt >= :fromInclusive
              and user.createdAt < :toExclusive
              and user.emailVerifiedAt is not null
              and user.emailVerifiedAt < :verifiedBefore
            """)
    long countVerifiedRegistrations(@Param("fromInclusive") Instant fromInclusive,
                                    @Param("toExclusive") Instant toExclusive,
                                    @Param("verifiedBefore") Instant verifiedBefore);

    @Query("""
            select count(user)
            from UserEntity user
            where user.createdAt >= :fromInclusive
              and user.createdAt < :toExclusive
              and user.emailVerified = true
              and user.emailVerifiedAt is null
            """)
    long countVerifiedRegistrationsWithoutTimestamp(@Param("fromInclusive") Instant fromInclusive,
                                                    @Param("toExclusive") Instant toExclusive);

    @Query("""
            select count(user)
            from UserEntity user
            where user.role in (com.grun.calorietracker.enums.UserRole.STANDARD, com.grun.calorietracker.enums.UserRole.PRO)
              and user.createdAt is not null
              and user.createdAt < :reportingCutoff
              and (user.lastActiveAt is null or user.lastActiveAt < :inactiveBefore)
            """)
    long countKnownInactiveUsers(@Param("reportingCutoff") Instant reportingCutoff,
                                 @Param("inactiveBefore") Instant inactiveBefore);

    @Query(value = """
            select coalesce(u.market_region, 'UNKNOWN') as dimension, count(*) as user_count
            from users u
            where u.created_at >= :fromInclusive
              and u.created_at < :toExclusive
            group by coalesce(u.market_region, 'UNKNOWN')
            order by user_count desc, dimension
            """, nativeQuery = true)
    List<Object[]> countRegistrationsByRegion(@Param("fromInclusive") Instant fromInclusive,
                                              @Param("toExclusive") Instant toExclusive);

    @Query(value = """
            select coalesce(u.preferred_language, 'UNKNOWN') as dimension, count(*) as user_count
            from users u
            where u.created_at >= :fromInclusive
              and u.created_at < :toExclusive
            group by coalesce(u.preferred_language, 'UNKNOWN')
            order by user_count desc, dimension
            """, nativeQuery = true)
    List<Object[]> countRegistrationsByLanguage(@Param("fromInclusive") Instant fromInclusive,
                                                @Param("toExclusive") Instant toExclusive);

    @Query("""
            select user.createdAt
            from UserEntity user
            where user.createdAt >= :fromInclusive
              and user.createdAt < :toExclusive
            order by user.createdAt
            """)
    List<Instant> findRegistrationTimestamps(@Param("fromInclusive") Instant fromInclusive,
                                             @Param("toExclusive") Instant toExclusive);
}
