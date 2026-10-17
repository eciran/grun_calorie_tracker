package com.grun.calorietracker.service.support;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PostgresFoodSearchCandidateProviderTest {
    @Test void oversizedInputNeverOpensAnIndexedDatabaseQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var provider = new PostgresFoodSearchCandidateProvider(jdbc);
        assertThrows(IllegalArgumentException.class, () -> provider.findCandidateIds("x".repeat(257)));
        assertThrows(IllegalArgumentException.class, () -> provider.findCandidateIds(String.join(" ", java.util.Collections.nCopies(25, "milk"))));
        verifyNoInteractions(jdbc);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"broccoli boiled", "meat chicken raw", "Acme broccoli", "haslanmis brokoli"})
    void candidateRestrictionKeepsCrossFieldAndReorderedTokenMatches(String query) {
        var source = new org.springframework.jdbc.datasource.SingleConnectionDataSource(
                "jdbc:h2:mem:token_candidates_" + query.hashCode(), "sa", "", true);
        JdbcTemplate jdbc = spy(new JdbcTemplate(source));
        try {
            jdbc.execute("create table food_items(id bigint, name varchar, display_name varchar, short_display_name varchar, brand varchar)");
            jdbc.execute("create table food_item_search_aliases(food_item_id bigint, alias varchar, normalized_alias varchar, active boolean)");
            jdbc.execute("create table food_item_localizations(food_item_id bigint, display_name varchar, short_display_name varchar, active boolean)");
            jdbc.update("insert into food_items(id,name,brand) values (1,'Boiled Broccoli','Acme'),(2,'Raw Chicken Meat',null)");
            jdbc.update("insert into food_item_search_aliases values (1,'Ha\u015flanm\u0131\u015f Brokoli','haslanmis brokoli',true)");
            doReturn(true).when(jdbc).execute(any(ConnectionCallback.class));
            var provider = new PostgresFoodSearchCandidateProvider(jdbc);
            long expected = query.contains("chicken") ? 2L : 1L;
            assertTrue(provider.findCandidateIds(query).orElseThrow().contains(expected), query);
        } finally {
            source.destroy();
        }
    }

    @Test
    void denseButBoundedCandidatesStayOnTheIndexedLosslessPath() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(true);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(java.util.stream.LongStream.rangeClosed(1, 5001).boxed().toList());
        assertEquals(5001, new PostgresFoodSearchCandidateProvider(jdbc)
                .findCandidateIds("broccoli boiled").orElseThrow().size());
    }

    @Test
    void extremelyBroadCandidatesFallBackInsteadOfTruncatingAwayLaterMatches() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(true);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(java.util.stream.LongStream.rangeClosed(
                        1, PostgresFoodSearchCandidateProvider.MAX_CANDIDATES + 1L).boxed().toList());
        assertTrue(new PostgresFoodSearchCandidateProvider(jdbc)
                .findCandidateIds("broccoli boiled").isEmpty());
    }

    @Test
    void candidateSqlFindsAllBrandVariantsWithoutAnAliasRow() {
        var source = new org.springframework.jdbc.datasource.SingleConnectionDataSource(
                "jdbc:h2:mem:brand_candidates", "sa", "", true);
        JdbcTemplate jdbc = spy(new JdbcTemplate(source));
        try {
            jdbc.execute("create table food_items(id bigint, name varchar, display_name varchar, short_display_name varchar, brand varchar)");
            jdbc.execute("create table food_item_search_aliases(food_item_id bigint, alias varchar, normalized_alias varchar, active boolean)");
            jdbc.execute("create table food_item_localizations(food_item_id bigint, display_name varchar, short_display_name varchar, active boolean)");
            String[] brands = {"Vit Hit", "Vit-Hit", "Vit\u2022Hit", "VitHit"};
            for (int i = 0; i < brands.length; i++) {
                jdbc.update("insert into food_items(id,name,brand) values (?,?,?)", i + 1, "Drink " + i, brands[i]);
            }
            doReturn(true).when(jdbc).execute(any(ConnectionCallback.class));
            var provider = new PostgresFoodSearchCandidateProvider(jdbc);
            for (String brand : brands) {
                assertEquals(List.of(1L, 2L, 3L, 4L), provider.findCandidateIds(brand).orElseThrow());
            }
        } finally {
            source.destroy();
        }
    }

    @Test
    void separatorBrandCandidateUsesBoundKeyAndMatchesPlaceholderCount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(true);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(7L, 8L));
        var provider = new PostgresFoodSearchCandidateProvider(jdbc);
        assertEquals(List.of(7L, 8L), provider.findCandidateIds("Vit\u2022Hit").orElseThrow());
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), eq(Long.class), args.capture());
        assertTrue(sql.getValue().contains(FoodBrandSearchRules.sqlExpression() + " = ?"));
        assertFalse(sql.getValue().contains("vithit"));
        assertEquals("vithit", args.getValue()[args.getValue().length - 1]);
        assertEquals(args.getValue().length, sql.getValue().chars().filter(c -> c == '?').count());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"M & S Milk", "Marks and Spencer Milk", "Milk M&S", "Vit-hit Apple"})
    void compoundBrandCandidatesStayParameterBound(String query) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(true);
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class))).thenReturn(List.of(7L));
        assertEquals(List.of(7L), new PostgresFoodSearchCandidateProvider(jdbc).findCandidateIds(query).orElseThrow());
        var sql = ArgumentCaptor.forClass(String.class); var args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(sql.capture(), eq(Long.class), args.capture());
        assertEquals(args.getValue().length, sql.getValue().chars().filter(c -> c == '?').count());
        assertFalse(sql.getValue().contains(query.toLowerCase(java.util.Locale.ROOT)));
        // A necessary product token is also a safe superset; not every query needs a brand-only candidate scan.
        String necessary = query.equals("Milk M&S") ? "%milk%" : query.contains("Vit") ? "vithit" : "m&s";
        assertTrue(java.util.Arrays.asList(args.getValue()).contains(necessary));
    }

    @Test
    void barcodeStillBypassesCandidateRestriction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.execute(any(ConnectionCallback.class))).thenReturn(true);
        assertTrue(new PostgresFoodSearchCandidateProvider(jdbc).findCandidateIds("5034033000961").isEmpty());
        verify(jdbc, never()).queryForList(anyString(), eq(Long.class), any(Object[].class));
    }
}
