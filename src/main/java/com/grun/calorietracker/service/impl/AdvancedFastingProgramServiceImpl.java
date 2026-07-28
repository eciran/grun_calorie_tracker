package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.AdvancedFastingProgramService;
import com.grun.calorietracker.service.support.AiIdempotencySupport;
import com.grun.calorietracker.service.support.FastingSafetyPolicy;
import com.grun.calorietracker.service.support.UserTimeZoneSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdvancedFastingProgramServiceImpl implements AdvancedFastingProgramService {
    private static final String CREATE_OPERATION = "CREATE_PROGRAM";
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final FastingProgramRepository programRepository;
    private final FastingProgramVersionRepository versionRepository;
    private final FastingProgramDayRuleRepository ruleRepository;
    private final FastingProgramIdempotencyRepository idempotencyRepository;
    private final UserTimeZoneSupport timeZoneSupport;

    @Override
    @Transactional
    public FastingProgramCreateResult create(String email, String idempotencyKey, FastingProgramRequestDto request) {
        String key = AiIdempotencySupport.normalizeKey(idempotencyKey);
        validate(request);
        UserEntity user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        String requestHash = requestHash(request);
        LocalDateTime now = timeZoneSupport.now(user);

        Optional<FastingProgramIdempotencyEntity> existing = idempotencyRepository
                .findByUserIdAndOperationAndIdempotencyKey(user.getId(), CREATE_OPERATION, key);
        if (existing.isPresent()) {
            FastingProgramIdempotencyEntity record = existing.get();
            if (record.getExpiresAt().isAfter(now)) {
                if (!record.getRequestHash().equals(requestHash)) {
                    throw new AdvancedFastingException(
                            AdvancedFastingErrorCode.IDEMPOTENCY_KEY_REUSED,
                            "Idempotency-Key was already used with a different fasting program payload.");
                }
                return new FastingProgramCreateResult(dto(record.getProgram()), true);
            }
            idempotencyRepository.delete(record);
            idempotencyRepository.flush();
        }

        FastingProgramEntity program = new FastingProgramEntity();
        program.setUser(user);
        applyMetadata(program, request);
        program.setStatus(FastingProgramStatus.DRAFT);
        program.setCurrentVersionNumber(1);
        program = programRepository.save(program);
        createVersion(program, 1, request.getRules());

        FastingProgramIdempotencyEntity record = new FastingProgramIdempotencyEntity();
        record.setUser(user);
        record.setOperation(CREATE_OPERATION);
        record.setIdempotencyKey(key);
        record.setRequestHash(requestHash);
        record.setProgram(program);
        record.setExpiresAt(now.plus(IDEMPOTENCY_RETENTION));
        idempotencyRepository.save(record);
        return new FastingProgramCreateResult(dto(program), false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FastingProgramDto> list(String email, List<String> statuses, boolean includeArchived) {
        UserEntity user = user(email);
        Set<FastingProgramStatus> requestedStatuses = parseStatuses(statuses);
        return programRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(program -> includeArchived || program.getStatus() != FastingProgramStatus.ARCHIVED)
                .filter(program -> requestedStatuses.isEmpty() || requestedStatuses.contains(program.getStatus()))
                .sorted(programListComparator())
                .map(this::dto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FastingProgramDto get(String email, Long id) {
        return dto(owned(user(email), id));
    }

    @Override
    @Transactional
    public FastingProgramDto update(String email, Long id, FastingProgramRequestDto request) {
        UserEntity user = user(email);
        validate(request);
        FastingProgramEntity program = owned(user, id);
        requireMutable(program, "updated");
        applyMetadata(program, request);
        int nextVersion = program.getCurrentVersionNumber() + 1;
        createVersion(program, nextVersion, request.getRules());
        program.setCurrentVersionNumber(nextVersion);
        return dto(programRepository.save(program));
    }

    @Override
    @Transactional(readOnly = true)
    public FastingProgramPreviewDto preview(String email, Long id, LocalDate startDate) {
        UserEntity user = user(email);
        FastingProgramEntity program = owned(user, id);
        LocalDate start = startDate == null ? timeZoneSupport.today(user) : startDate;
        FastingProgramVersionEntity version = currentVersion(program);
        Map<DayOfWeek, FastingProgramDayRuleEntity> rules = ruleRepository
                .findAllByProgramVersionIdOrderByDayOfWeek(version.getId()).stream()
                .collect(Collectors.toMap(FastingProgramDayRuleEntity::getDayOfWeek, Function.identity()));
        ZoneId zone = timeZoneSupport.zoneId(user);
        List<FastingProgramPreviewDto.PreviewDay> days = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            LocalDate date = start.plusDays(index);
            FastingProgramDayRuleEntity rule = rules.get(date.getDayOfWeek());
            ZonedDateTime startAt = null;
            ZonedDateTime endAt = null;
            if (rule.getRuleType() == FastingDayRuleType.FAST) {
                startAt = date.atTime(rule.getPreferredStartTime()).atZone(zone);
                endAt = startAt.plusMinutes(rule.getFastingMinutes());
            }
            days.add(new FastingProgramPreviewDto.PreviewDay(
                    date, rule.getRuleType(), startAt, endAt, rule.getReducedCalorieTarget()));
        }
        return new FastingProgramPreviewDto(
                program.getId(), version.getVersionNumber(), zone.getId(), start, List.copyOf(days));
    }

    @Override
    @Transactional
    public FastingProgramDto activate(String email, Long id) {
        UserEntity user = user(email);
        List<FastingProgramEntity> programs = programRepository.findAllByUserIdForUpdate(user.getId());
        FastingProgramEntity target = programs.stream()
                .filter(program -> program.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Fasting program not found."));
        if (target.getStatus() == FastingProgramStatus.ACTIVE) {
            return dto(target);
        }
        requireMutable(target, "activated");
        List<FastingProgramEntity> previous = programs.stream()
                .filter(program -> program.getStatus() == FastingProgramStatus.ACTIVE)
                .toList();
        previous.forEach(program -> program.setStatus(FastingProgramStatus.PAUSED));
        programRepository.saveAll(previous);
        programRepository.flush();
        target.setStatus(FastingProgramStatus.ACTIVE);
        if (target.getEffectiveFrom() == null) {
            target.setEffectiveFrom(timeZoneSupport.today(user));
        }
        return dto(programRepository.save(target));
    }

    @Override
    @Transactional
    public FastingProgramDto pause(String email, Long id) {
        FastingProgramEntity program = owned(user(email), id);
        if (program.getStatus() == FastingProgramStatus.PAUSED) {
            return dto(program);
        }
        if (program.getStatus() == FastingProgramStatus.DRAFT) {
            throw new AdvancedFastingException(
                    AdvancedFastingErrorCode.INVALID_FASTING_PROGRAM_TRANSITION,
                    "A draft fasting program cannot be paused.");
        }
        requireMutable(program, "paused");
        program.setStatus(FastingProgramStatus.PAUSED);
        return dto(programRepository.save(program));
    }

    @Override
    @Transactional
    public FastingProgramDto archive(String email, Long id) {
        FastingProgramEntity program = owned(user(email), id);
        if (program.getStatus() == FastingProgramStatus.ARCHIVED) {
            return dto(program);
        }
        program.setStatus(FastingProgramStatus.ARCHIVED);
        return dto(programRepository.save(program));
    }

    private void requireMutable(FastingProgramEntity program, String action) {
        if (program.getStatus() == FastingProgramStatus.ARCHIVED) {
            throw new AdvancedFastingException(
                    AdvancedFastingErrorCode.ARCHIVED_FASTING_PROGRAM,
                    "Archived fasting programs cannot be " + action + ".");
        }
    }

    private Set<FastingProgramStatus> parseStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return EnumSet.noneOf(FastingProgramStatus.class);
        }
        EnumSet<FastingProgramStatus> parsed = EnumSet.noneOf(FastingProgramStatus.class);
        for (String value : statuses) {
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String token : value.split(",")) {
                try {
                    parsed.add(FastingProgramStatus.valueOf(token.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    throw new AdvancedFastingException(
                            AdvancedFastingErrorCode.INVALID_FASTING_PROGRAM_STATUS_FILTER,
                            "Unsupported fasting program status filter.");
                }
            }
        }
        return parsed;
    }

    private Comparator<FastingProgramEntity> programListComparator() {
        return Comparator.comparingInt((FastingProgramEntity program) -> statusRank(program.getStatus()))
                .thenComparing(FastingProgramEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(FastingProgramEntity::getId, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private int statusRank(FastingProgramStatus status) {
        return switch (status) {
            case ACTIVE -> 0;
            case DRAFT -> 1;
            case PAUSED -> 2;
            case ARCHIVED -> 3;
        };
    }

    private String requestHash(FastingProgramRequestDto request) {
        String rules = request.getRules().stream()
                .sorted(Comparator.comparing(FastingDayRuleRequestDto::getDayOfWeek))
                .map(rule -> String.join("|",
                        rule.getDayOfWeek().name(),
                        rule.getRuleType().name(),
                        Objects.toString(rule.getFastingMinutes(), ""),
                        Objects.toString(rule.getPreferredStartTime(), ""),
                        Objects.toString(rule.getReducedCalorieTarget(), "")))
                .collect(Collectors.joining(";"));
        String canonical = request.getName().trim() + "\n"
                + Objects.toString(request.getEffectiveFrom(), "") + "\n"
                + Objects.toString(request.getEffectiveUntil(), "") + "\n"
                + rules;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private void applyMetadata(FastingProgramEntity program, FastingProgramRequestDto request) {
        if (request.getEffectiveFrom() != null && request.getEffectiveUntil() != null
                && request.getEffectiveUntil().isBefore(request.getEffectiveFrom())) {
            throw new AdvancedFastingException(
                    AdvancedFastingErrorCode.INVALID_FASTING_PROGRAM_DATES,
                    "effectiveUntil cannot be before effectiveFrom.");
        }
        program.setName(request.getName().trim());
        program.setEffectiveFrom(request.getEffectiveFrom());
        program.setEffectiveUntil(request.getEffectiveUntil());
    }

    private void validate(FastingProgramRequestDto request) {
        if (request.getRules() == null || request.getRules().size() != 7) {
            throw new AdvancedFastingException(
                    AdvancedFastingErrorCode.INVALID_WEEKLY_FASTING_RULES,
                    "Exactly seven weekday rules are required.");
        }
        Set<DayOfWeek> days = request.getRules().stream()
                .map(FastingDayRuleRequestDto::getDayOfWeek)
                .collect(Collectors.toSet());
        if (days.size() != 7) {
            throw new AdvancedFastingException(
                    AdvancedFastingErrorCode.INVALID_WEEKLY_FASTING_RULES,
                    "Each weekday must be configured exactly once.");
        }
        List<DayOfWeek> reducedDays = new ArrayList<>();
        for (FastingDayRuleRequestDto rule : request.getRules()) {
            if (rule.getRuleType() == FastingDayRuleType.FAST) {
                if (rule.getFastingMinutes() == null || rule.getPreferredStartTime() == null
                        || rule.getReducedCalorieTarget() != null) {
                    throw new AdvancedFastingException(
                            AdvancedFastingErrorCode.INVALID_FASTING_DAY_RULE,
                            "FAST rules require time and duration only.");
                }
                if (rule.getFastingMinutes() > FastingSafetyPolicy.MAX_CONTINUOUS_FASTING_HOURS * 60) {
                    throw new FastingSafetyException(
                            FastingSafetyErrorCode.FASTING_UNSAFE_DURATION,
                            "Continuous fasting cannot exceed 24 hours.");
                }
            } else if (rule.getRuleType() == FastingDayRuleType.REDUCED_CALORIE) {
                if (rule.getReducedCalorieTarget() == null || rule.getFastingMinutes() != null
                        || rule.getPreferredStartTime() != null) {
                    throw new AdvancedFastingException(
                            AdvancedFastingErrorCode.INVALID_FASTING_DAY_RULE,
                            "REDUCED_CALORIE rules require a calorie target only.");
                }
                reducedDays.add(rule.getDayOfWeek());
            } else if (rule.getFastingMinutes() != null || rule.getPreferredStartTime() != null
                    || rule.getReducedCalorieTarget() != null) {
                throw new AdvancedFastingException(
                        AdvancedFastingErrorCode.INVALID_FASTING_DAY_RULE,
                        "NORMAL and REST rules cannot contain fasting targets.");
            }
        }
        if (!reducedDays.isEmpty()) {
            if (reducedDays.size() != 2) {
                throw new AdvancedFastingException(
                        AdvancedFastingErrorCode.INVALID_FIVE_TWO_SCHEDULE,
                        "A 5:2 program requires exactly two reduced-calorie days.");
            }
            int first = reducedDays.get(0).getValue();
            int second = reducedDays.get(1).getValue();
            if (Math.abs(first - second) == 1 || Math.abs(first - second) == 6) {
                throw new AdvancedFastingException(
                        AdvancedFastingErrorCode.INVALID_FIVE_TWO_SCHEDULE,
                        "Reduced-calorie days must not be consecutive.");
            }
        }
    }

    private void createVersion(FastingProgramEntity program, int number,
                               List<FastingDayRuleRequestDto> requests) {
        FastingProgramVersionEntity version = new FastingProgramVersionEntity();
        version.setProgram(program);
        version.setVersionNumber(number);
        version.setSafetyPolicyVersion(FastingSafetyPolicy.VERSION);
        version = versionRepository.save(version);
        for (FastingDayRuleRequestDto request : requests) {
            FastingProgramDayRuleEntity rule = new FastingProgramDayRuleEntity();
            rule.setProgramVersion(version);
            rule.setDayOfWeek(request.getDayOfWeek());
            rule.setRuleType(request.getRuleType());
            rule.setFastingMinutes(request.getFastingMinutes());
            rule.setPreferredStartTime(request.getPreferredStartTime());
            rule.setReducedCalorieTarget(request.getReducedCalorieTarget());
            ruleRepository.save(rule);
        }
    }

    private FastingProgramDto dto(FastingProgramEntity program) {
        FastingProgramVersionEntity version = currentVersion(program);
        List<FastingProgramDto.FastingDayRuleDto> rules = ruleRepository
                .findAllByProgramVersionIdOrderByDayOfWeek(version.getId()).stream()
                .map(rule -> new FastingProgramDto.FastingDayRuleDto(
                        rule.getId(), rule.getDayOfWeek(), rule.getRuleType(), rule.getFastingMinutes(),
                        rule.getPreferredStartTime(), rule.getReducedCalorieTarget()))
                .toList();
        return new FastingProgramDto(
                program.getId(), program.getName(), program.getStatus(), program.getEffectiveFrom(),
                program.getEffectiveUntil(), version.getVersionNumber(), program.getVersion(),
                version.getSafetyPolicyVersion(), program.getCreatedAt(), program.getUpdatedAt(), rules);
    }

    private FastingProgramVersionEntity currentVersion(FastingProgramEntity program) {
        return versionRepository.findByProgramIdAndVersionNumber(
                        program.getId(), program.getCurrentVersionNumber())
                .orElseThrow(() -> new IllegalStateException("Fasting program version is missing."));
    }

    private FastingProgramEntity owned(UserEntity user, Long id) {
        return programRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Fasting program not found."));
    }

    private UserEntity user(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
    }
}