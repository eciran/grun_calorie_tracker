package com.grun.calorietracker.service.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Repository
public class OwnerErrorStore {
    private final JdbcTemplate jdbc;
    public OwnerErrorStore(JdbcTemplate jdbc) { this.jdbc = new JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource())); this.jdbc.setQueryTimeout(3); }
    private static final RowMapper<OwnerErrorEvent> ROW = (rs, row) -> new OwnerErrorEvent(rs.getLong("id"), rs.getObject("event_key", UUID.class),
        rs.getTimestamp("occurred_at").toInstant(), (Integer)rs.getObject("status"), rs.getString("method"), rs.getString("route"),
        rs.getString("correlation_id"), rs.getString("error_code"), rs.getString("exception_type"),
        rs.getString("technical_location"), rs.getLong("duration_ms"), rs.getString("source"), rs.getString("client_platform"), rs.getString("app_version"));

    public void insert(OwnerErrorEvent event) {
        jdbc.update("INSERT INTO owner_error_events (event_key,occurred_at,status,method,route,correlation_id,error_code,exception_type,technical_location,duration_ms,source,client_platform,app_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
            event.eventKey(), Timestamp.from(event.occurredAt()), event.status(), event.method(), event.route(), event.correlationId(),
            event.errorCode(), event.exceptionType(), event.technicalLocation(), event.durationMs(), event.source(), event.clientPlatform(), event.appVersion());
    }
    public record Query(Instant from, Instant to, Integer status, Integer statusClass, String source, String method, String route,
                        String correlationId, String errorCode, int page, int size) { }
    public record Page(List<OwnerErrorEvent> content, long totalElements, int totalPages, int page, int size) { }
    public record Group(String fingerprint, String source, Integer status, String method, String route, String errorCode,
                        long occurrenceCount, Instant firstOccurredAt, Instant lastOccurredAt,
                        long maxDurationMs, long affectedVersions, String lifecycleStatus, String reason,
                        String updatedBy, Instant updatedAt) { }
    public record GroupIdentity(String source,Integer status,String method,String route,String errorCode) { }
    public record LifecycleSummary(long newGroups,long investigatingGroups,long resolvedGroups,long reopenedGroups) { }
    public Page find(Query query) {
        StringBuilder where = new StringBuilder(" WHERE occurred_at >= ? AND occurred_at <= ?");
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(query.from()), Timestamp.from(query.to())));
        if (query.status() != null) { where.append(" AND status = ?"); args.add(query.status()); }
        if (query.statusClass() != null) { where.append(" AND status >= ? AND status < ?"); args.add(query.statusClass()*100); args.add((query.statusClass()+1)*100); }
        for (var field : List.of(new String[]{"source", query.source()}, new String[]{"method", query.method()}, new String[]{"route", query.route()},
                new String[]{"correlation_id", query.correlationId()}, new String[]{"error_code", query.errorCode()})) {
            if (field[1] != null && !field[1].isBlank()) { where.append(" AND ").append(field[0]).append(" = ?"); args.add(field[1]); }
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM owner_error_events" + where, Long.class, args.toArray());
        args.add(query.size()); args.add((long) query.page()*query.size());
        var rows = jdbc.query("SELECT * FROM owner_error_events" + where + " ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?", ROW, args.toArray());
        long count = total == null ? 0 : total;
        return new Page(rows, count, (int)Math.ceil((double)count/query.size()), query.page(), query.size());
    }
    public Optional<OwnerErrorEvent> detail(long id) {
        return jdbc.query("SELECT * FROM owner_error_events WHERE id = ?", ROW, id).stream().findFirst();
    }
    public List<Group> groups(Instant from, Instant to, String source, int limit) {
        String sourceClause = source == null || source.isBlank() ? "" : " AND source = ?";
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(from), Timestamp.from(to)));
        if (!sourceClause.isEmpty()) args.add(source);
        args.add(limit);
        var groups=jdbc.query("SELECT source,status,method,route,error_code,COUNT(*) occurrence_count," +
                        "MIN(occurred_at) first_occurred_at,MAX(occurred_at) last_occurred_at," +
                        "MAX(duration_ms) max_duration_ms,COUNT(DISTINCT app_version) affected_versions " +
                        "FROM owner_error_events WHERE occurred_at >= ? AND occurred_at <= ?" + sourceClause +
                        " GROUP BY source,status,method,route,error_code ORDER BY occurrence_count DESC,last_occurred_at DESC LIMIT ?",
                (rs,row) -> { var identity=new GroupIdentity(rs.getString("source"),(Integer)rs.getObject("status"),rs.getString("method"),rs.getString("route"),rs.getString("error_code")); return new Group(fingerprint(identity),identity.source(),identity.status(),identity.method(),
                        identity.route(),identity.errorCode(),rs.getLong("occurrence_count"),
                        rs.getTimestamp("first_occurred_at").toInstant(),rs.getTimestamp("last_occurred_at").toInstant(),
                        rs.getLong("max_duration_ms"),rs.getLong("affected_versions"),"NEW",null,null,null); }, args.toArray());
        return groups.stream().map(group -> jdbc.query("SELECT lifecycle_status,reason,updated_by,updated_at FROM owner_error_group_states WHERE fingerprint=?",
                rs -> rs.next()?new Group(group.fingerprint(),group.source(),group.status(),group.method(),group.route(),group.errorCode(),group.occurrenceCount(),group.firstOccurredAt(),group.lastOccurredAt(),group.maxDurationMs(),group.affectedVersions(),rs.getString(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant()):group,
                group.fingerprint())).toList();
    }
    public boolean groupExists(GroupIdentity value){
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM owner_error_events WHERE source=? AND status IS NOT DISTINCT FROM ? AND method=? AND route=? AND error_code IS NOT DISTINCT FROM ?",Long.class,value.source(),value.status(),value.method(),value.route(),value.errorCode());
        return count!=null&&count>0;
    }
    public Optional<String> lifecycle(String fingerprint){return jdbc.query("SELECT lifecycle_status FROM owner_error_group_states WHERE fingerprint=? FOR UPDATE",(rs,row)->rs.getString(1),fingerprint).stream().findFirst();}
    public void saveLifecycle(String fingerprint,GroupIdentity value,String previousStatus,String status,String reason,String owner,Instant now){
        int changed=jdbc.update("UPDATE owner_error_group_states SET lifecycle_status=?,reason=?,updated_by=?,updated_at=? WHERE fingerprint=?",status,reason,owner,Timestamp.from(now),fingerprint);
        if(changed==0)try{jdbc.update("INSERT INTO owner_error_group_states(fingerprint,source,status,method,route,error_code,lifecycle_status,reason,updated_by,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
                fingerprint,value.source(),value.status(),value.method(),value.route(),value.errorCode(),status,reason,owner,Timestamp.from(now));}
        catch(org.springframework.dao.DuplicateKeyException race){jdbc.update("UPDATE owner_error_group_states SET lifecycle_status=?,reason=?,updated_by=?,updated_at=? WHERE fingerprint=?",status,reason,owner,Timestamp.from(now),fingerprint);}
        jdbc.update("INSERT INTO owner_error_group_state_history(fingerprint,previous_status,lifecycle_status,reason,updated_by,occurred_at) VALUES(?,?,?,?,?,?)",
                fingerprint,previousStatus,status,reason,owner,Timestamp.from(now));
    }
    public static String fingerprint(GroupIdentity value){
        try{String raw=String.join("\u001f",value.source(),Objects.toString(value.status(),""),value.method(),value.route(),Objects.toString(value.errorCode(),""));return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public LifecycleSummary lifecycleSummary(Instant from,Instant to){
        Long newGroups=jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT source,status,method,route,error_code FROM owner_error_events GROUP BY source,status,method,route,error_code HAVING MIN(occurred_at)>=? AND MIN(occurred_at)<?) grouped",Long.class,Timestamp.from(from),Timestamp.from(to));
        Long investigating=jdbc.queryForObject("SELECT COUNT(*) FROM owner_error_group_states WHERE lifecycle_status='INVESTIGATING'",Long.class);
        Long resolved=jdbc.queryForObject("SELECT COUNT(*) FROM owner_error_group_state_history WHERE lifecycle_status='RESOLVED' AND occurred_at>=? AND occurred_at<?",Long.class,Timestamp.from(from),Timestamp.from(to));
        Long reopened=jdbc.queryForObject("SELECT COUNT(*) FROM owner_error_group_state_history WHERE lifecycle_status='REOPENED' AND occurred_at>=? AND occurred_at<?",Long.class,Timestamp.from(from),Timestamp.from(to));
        return new LifecycleSummary(orZero(newGroups),orZero(investigating),orZero(resolved),orZero(reopened));
    }
    private long orZero(Long value){return value==null?0:value;}
    public void prune(Instant before, int maxRows) {
        jdbc.update("DELETE FROM owner_error_events WHERE id IN (SELECT id FROM owner_error_events WHERE occurred_at < ? ORDER BY occurred_at,id LIMIT 1000)", Timestamp.from(before));
        jdbc.update("DELETE FROM owner_error_events WHERE id IN (SELECT id FROM owner_error_events ORDER BY id DESC LIMIT 1000 OFFSET ?)", maxRows);
    }
}
