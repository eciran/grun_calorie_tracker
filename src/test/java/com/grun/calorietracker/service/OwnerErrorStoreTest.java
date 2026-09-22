package com.grun.calorietracker.service;

import com.grun.calorietracker.service.support.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerErrorStoreTest {
    @TempDir Path temp;
    private OwnerErrorEvent event(int status, Instant at) { return new OwnerErrorEvent(null,UUID.randomUUID(),at,status,"GET","/api/v1/users/{id}",UUID.randomUUID().toString(),"INVALID_REQUEST",null,null,12,"BACKEND",null,null); }
    @Test void persistsAcrossConnectionsAndFiltersPagesWithoutSqlInjection() {
        String url="jdbc:h2:file:"+temp.resolve("errors").toAbsolutePath()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        var ds=new DriverManagerDataSource(url,"sa","");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V266__owner_error_center.sql"),new ClassPathResource("db/migration/V270__add_owner_error_sources.sql"),new ClassPathResource("db/migration/V271__owner_error_group_lifecycle.sql"),new ClassPathResource("db/migration/V273__owner_error_group_state_history.sql")).execute(ds);
        var store=new OwnerErrorStore(new JdbcTemplate(ds)); var now=Instant.now();
        var first=event(400,now); store.insert(first); store.insert(first); store.insert(event(500,now));
        store=new OwnerErrorStore(new JdbcTemplate(new DriverManagerDataSource(url,"sa","")));
        var query=new OwnerErrorStore.Query(now.minusSeconds(60),now.plusSeconds(60),null,null,null,null,null,null,null,0,1);
        var page=store.find(query); assertEquals(2,page.totalElements()); assertEquals(2,page.totalPages()); assertEquals(1,page.content().size());
        assertTrue(store.detail(page.content().get(0).id()).isPresent());
        assertEquals(1,store.find(new OwnerErrorStore.Query(query.from(),query.to(),400,4,"BACKEND","GET",first.route(),first.correlationId(),first.errorCode(),0,25)).totalElements());
        var client = new OwnerErrorEvent(null,UUID.randomUUID(),now,null,"GET","/api/v1/products/{id}",null,"CLIENT_NETWORK",null,null,80,"ADMIN_WEB","BROWSER","admin-ui-v2");
        store.insert(client);
        var clientPage=store.find(new OwnerErrorStore.Query(query.from(),query.to(),null,null,"ADMIN_WEB",null,null,null,null,0,25));
        assertEquals(1,clientPage.totalElements());
        assertNull(clientPage.content().get(0).status());
        assertEquals("BROWSER",clientPage.content().get(0).clientPlatform());
        var groups=store.groups(query.from(),query.to(),null,10);
        assertEquals(3,groups.size());
        assertEquals(1,groups.stream().filter(group->"ADMIN_WEB".equals(group.source())).findFirst().orElseThrow().affectedVersions());
        assertEquals(1,store.groups(query.from(),query.to(),"ADMIN_WEB",10).size());
        var backendGroup=groups.stream().filter(group->group.status()!=null&&group.status()==500).findFirst().orElseThrow();
        var identity=new OwnerErrorStore.GroupIdentity(backendGroup.source(),backendGroup.status(),backendGroup.method(),backendGroup.route(),backendGroup.errorCode());
        assertTrue(store.groupExists(identity)); store.saveLifecycle(backendGroup.fingerprint(),identity,"NEW","INVESTIGATING","Review started","owner@example.com",now);
        var updated=store.groups(query.from(),query.to(),"BACKEND",10).stream().filter(group->group.fingerprint().equals(backendGroup.fingerprint())).findFirst().orElseThrow();
        assertEquals("INVESTIGATING",updated.lifecycleStatus()); assertEquals("Review started",updated.reason());
        store.saveLifecycle(backendGroup.fingerprint(),identity,"INVESTIGATING","RESOLVED","Fix verified","owner@example.com",now);
        store.saveLifecycle(backendGroup.fingerprint(),identity,"RESOLVED","REOPENED","Failure returned","owner@example.com",now);
        var lifecycle=store.lifecycleSummary(query.from(),query.to());
        assertEquals(3,lifecycle.newGroups()); assertEquals(0,lifecycle.investigatingGroups());
        assertEquals(1,lifecycle.resolvedGroups()); assertEquals(1,lifecycle.reopenedGroups());
        assertEquals(0,store.find(new OwnerErrorStore.Query(query.from(),query.to(),null,null,null,null,"' OR 1=1 --",null,null,0,25)).totalElements());
        store.insert(event(500,now.minusSeconds(86400*40))); store.prune(now.minusSeconds(86400*30),1);
        assertEquals(1,store.find(query).totalElements());
    }
    @Test void retriesAreBoundedAndQueueOverflowIsVisible() {
        var store=mock(OwnerErrorStore.class); var recorder=new OwnerErrorRecorder(store,30,100000);
        try {
            doThrow(new IllegalStateException("database unavailable")).when(store).insert(any());
            recorder.record(event(500,Instant.now()));
            recorder.drain(); recorder.drain(); recorder.drain();
            assertEquals(3,recorder.health().writeFailures()); assertEquals(1,recorder.health().dropped()); assertEquals(0,recorder.health().queued());
            for(int i=0;i<2001;i++) recorder.record(event(400,Instant.now()));
            assertEquals(2000,recorder.health().queued()); assertEquals(2,recorder.health().dropped());
        } finally { recorder.stop(); }
    }
    @Test void savedServerErrorIsForwardedWithoutAlertFailurePoisoningErrorCapture() {
        var store=mock(OwnerErrorStore.class); var alerts=mock(OwnerOperationalAlertService.class);
        var recorder=new OwnerErrorRecorder(store,30,100000); recorder.setOwnerOperationalAlertService(alerts);
        try {
            var value=event(503,Instant.now()); doThrow(new IllegalStateException("outbox unavailable")).when(alerts).recordCriticalBackendError(value);
            recorder.record(value); recorder.drain();
            verify(store).insert(value); verify(alerts).recordCriticalBackendError(value);
            assertEquals(1,recorder.health().saved()); assertEquals(0,recorder.health().writeFailures()); assertEquals(0,recorder.health().queued());
        } finally { recorder.stop(); }
    }
}
