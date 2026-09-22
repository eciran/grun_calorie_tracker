package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.EmailAddress;
import com.grun.calorietracker.exception.RequestConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class UserEmailIdentityTest {
    @Autowired UserRepository repository;
    @Autowired TestEntityManager em;

    @Test
    void findsHistoricalMixedCaseAddressForLoginAndOrdinaryLookups() {
        Long id = create("legacy@example.com");
        legacyEmail(id, "Legacy@Example.COM");
        assertEquals(id, repository.findByEmail(" legacy@example.com ").orElseThrow().getId());
        assertEquals(id, repository.findByEmailForUpdate("LEGACY@example.com").orElseThrow().getId());
    }

    @Test
    void ambiguousLegacyAccountsAreNeverSelectedArbitrarily() {
        create("same@example.com");
        Long other = create("other@example.com");
        legacyEmail(other, "Same@example.com");
        assertThrows(RequestConflictException.class, () -> repository.findByEmail("same@example.com"));
        assertThrows(RequestConflictException.class, () -> repository.findByEmailForUpdate("Same@example.com"));
        assertEquals(2, repository.count());
    }

    @Test
    void newAddressesAreCanonicalWithoutMergingGmailAliases() {
        Long id = create(" First.Last+tag@GMAIL.COM ");
        assertEquals("first.last+tag@gmail.com", em.find(UserEntity.class, id).getEmail());
        assertTrue(repository.findByEmail("firstlast@gmail.com").isEmpty());
        assertTrue(repository.findByEmail(null).isEmpty());
        assertTrue(repository.findByEmailForUpdate("  ").isEmpty());
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("initial@example.com", EmailAddress.canonical("INITIAL@EXAMPLE.COM"));
        } finally { Locale.setDefault(previous); }
    }

    private Long create(String email) {
        UserEntity user = new UserEntity(); user.setEmail(email);
        return em.persistAndFlush(user).getId();
    }

    private void legacyEmail(Long id, String email) {
        em.getEntityManager().createNativeQuery("UPDATE users SET email = :email WHERE id = :id")
                .setParameter("email", email).setParameter("id", id).executeUpdate();
        em.clear();
    }
}
