package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Ticker;
import com.grun.calorietracker.config.FoodProductCacheProperties;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.service.support.FoodProductCacheRevision;
import com.grun.calorietracker.service.support.FoodProductSnapshotCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "GRUN_RUN_PRODUCT_CACHE_POSTGRES", matches = "true")
class ProductCachePostgresIntegrationTest {
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    static Flyway flyway;
    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    FoodProductCacheProperties properties = new FoodProductCacheProperties();
    FoodProductCacheRevision revision;
    FoodProductSnapshotCache first;
    FoodProductSnapshotCache second;
    long id;

    @BeforeAll static void initializeOwnedDb() {
        String url = System.getenv("GRUN_PRODUCT_CACHE_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/grun_product_cache_test"))
            throw new IllegalStateException("Only the disposable localhost product-cache DB is allowed");
        source = new DriverManagerDataSource(url, "r7_test", "");
        jdbc = new JdbcTemplate(source);
        flyway = Flyway.configure().dataSource(source).cleanDisabled(true).baselineOnMigrate(false)
                .locations("classpath:db/migration").load();
        flyway.migrate();
    }

    @BeforeEach void prepare() {
        revision = new FoodProductCacheRevision(jdbc, meters, properties);
        first = cache(); second = cache();
        id = jdbc.queryForObject("insert into food_items(name, calories, protein, fat, carbs) values ('Original',100,1,1,1) returning id", Long.class);
    }
    FoodProductSnapshotCache cache() {
        return new FoodProductSnapshotCache("foodProductById", properties.getDetail(), revision,
                new ObjectMapper().findAndRegisterModules(), meters, true, Ticker.systemTicker());
    }
    FoodProductDto product(String name) { var value = new FoodProductDto(); value.setProductName(name); return value; }
    FoodProductCacheRevision.Key key() { return revision.key("product:" + id); }

    @Test void validatesFullAdditiveMigrationAndTriggerCoverage() {
        assertTrue(java.util.Arrays.stream(flyway.info().applied())
                .anyMatch(migration -> "311".equals(migration.getVersion().getVersion())),
                "The required product evidence migration must be applied, even when unrelated migrations follow it");
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.info().pending().length);
        assertEquals(17L, jdbc.queryForObject("select count(*) from pg_trigger where tgname like 'trg_product_cache_fence%'", Long.class));
        assertNotNull(revision.current());
        assertEquals("Original", jdbc.queryForObject("select name from food_items where id=?", String.class, id));
    }

    @Test void committedUpdateInvalidatesTwoIndependentInstancesWithoutRestart() {
        var old = key(); first.put(old, product("Original")); second.put(old, product("Original"));
        jdbc.update("update food_items set name='New' where id=?", id);
        var fresh = key(); assertNotEquals(old.revision(), fresh.revision());
        assertNull(first.get(fresh)); assertNull(second.get(fresh));
        first.put(fresh, product("New")); assertEquals("New", first.get(fresh, FoodProductDto.class).getProductName());
    }

    @Test void pendingWriterBypassesCacheAndRollbackCannotPublish() throws Exception {
        var old = key(); first.put(old, product("Original"));
        try (Connection writer = source.getConnection()) {
            writer.setAutoCommit(false);
            try (var statement = writer.prepareStatement("update food_items set name='Pending' where id=?")) {
                statement.setLong(1, id); statement.executeUpdate();
            }
            assertNull(revision.current()); assertNull(second.get(key()));
            assertEquals("Original", jdbc.queryForObject("select name from food_items where id=?", String.class, id));
            writer.rollback();
        }
        assertNotNull(revision.current()); assertNotEquals(old.revision(), key().revision());
        assertEquals("Original", jdbc.queryForObject("select name from food_items where id=?", String.class, id));
    }

    @Test void olderSequenceWriterCommittingLastNeverReusesIntermediateCache() throws Exception {
        long other = jdbc.queryForObject("insert into food_items(name) values ('Other') returning id", Long.class);
        try (Connection early = source.getConnection(); Connection late = source.getConnection()) {
            early.setAutoCommit(false); late.setAutoCommit(false);
            try (var statement = early.createStatement()) { statement.executeUpdate("update food_items set name='Early' where id=" + id); }
            try (var statement = late.createStatement()) { statement.executeUpdate("update food_items set name='Late' where id=" + other); }
            late.commit(); assertNull(revision.current());
            early.commit(); assertNotNull(revision.current());
        }
        assertEquals("Early", jdbc.queryForObject("select name from food_items where id=?", String.class, id));
    }

    @Test void staleLoaderIsFencedAndAfterCommitPutBecomesVisible() {
        var old = key(); jdbc.update("update food_items set name='Changed' where id=?", id);
        first.put(old, product("Old loader")); assertNull(first.get(old));
        var fresh = key();
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> {
            first.put(fresh, product("Committed")); assertNull(first.get(fresh));
        });
        assertNotNull(first.get(fresh));
    }

    @Test void sourceAndPortionMetadataUpdatesAreFencedButPopularityUsesTtl() {
        Long before = revision.current();
        jdbc.update("update food_items set usage_count=coalesce(usage_count,0)+1, search_selection_count=coalesce(search_selection_count,0)+1 where id=?", id);
        assertEquals(before, revision.current());
        jdbc.update("insert into food_item_serving_options(food_item_id,label,unit_type,quantity,gram_weight) values (?, '1 slice', 'SLICE', 1, 20)", id);
        assertNotEquals(before, revision.current());
        before = revision.current();
        jdbc.update("insert into food_item_localizations(food_item_id,language,display_name) values (?, 'TR', 'Test')", id);
        assertNotEquals(before, revision.current());
    }

    @Test void cacheFailureDoesNotPoisonOuterDatabaseTransaction() {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.executeWithoutResult(status -> {
            assertNotNull(revision.current());
            jdbc.execute("create or replace function public.grun_product_cache_stamp() returns bigint language plpgsql as $$ begin raise exception 'owned test cache failure'; end $$");
            assertNull(revision.current());
            assertEquals("Original", jdbc.queryForObject("select name from food_items where id=?", String.class, id));
            status.setRollbackOnly();
        });
        assertNotNull(revision.current());
    }

    @Test void higherIsolationNeverCachesAnOlderSnapshotUnderNewStamp() {
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        tx.executeWithoutResult(status -> assertNull(revision.current()));
    }

    @Test void readersNeverLeakSessionLocks() throws Exception {
        try (var connection = source.getConnection()) {
            var held = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            var persistent = new FoodProductCacheRevision(new JdbcTemplate(held), meters, properties);
            for (int i = 0; i < 50; i++) assertNotNull(persistent.current());
            assertEquals(0L, jdbc.queryForObject("select count(*) from pg_locks where locktype='advisory' and classid=17491 and objid=308", Long.class));
        }
    }

    @Test void concurrentReadersNeverMistakeOtherReadersForPendingWriters() throws Exception {
        var pool = Executors.newFixedThreadPool(8); var start = new CyclicBarrier(8);
        var results = new java.util.ArrayList<Future<Integer>>();
        try {
            for (int i = 0; i < 8; i++) results.add(pool.submit(() -> {
                try (var connection = source.getConnection()) {
                    var held = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
                    var reader = new FoodProductCacheRevision(new JdbcTemplate(held), meters, properties);
                    start.await(3, TimeUnit.SECONDS);
                    int unavailable = 0;
                    for (int n = 0; n < 50; n++) if (reader.current() == null) unavailable++;
                    return unavailable;
                }
            }));
            for (var result : results) assertEquals(0, result.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)); }
    }

    @Test void legacyExclusiveStampReaderIsNotTreatedAsAWriteFence() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("select pg_advisory_lock(17491,308)");
            try { assertNotNull(revision.current()); }
            finally { statement.execute("select pg_advisory_unlock(17491,308)"); }
        }
    }
    @AfterEach void cleanup() { meters.close(); }
}
