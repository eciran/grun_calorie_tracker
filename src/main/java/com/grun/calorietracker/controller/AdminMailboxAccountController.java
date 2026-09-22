package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminMailboxAccountDto;
import com.grun.calorietracker.dto.AdminMailboxAccountRequestDto;
import com.grun.calorietracker.dto.AdminMailboxMessageDto;
import com.grun.calorietracker.dto.AdminMailboxMessageDetailDto;
import com.grun.calorietracker.service.AdminMailboxAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/mail/mailboxes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMailboxAccountController {
    private final AdminMailboxAccountService service;
    @GetMapping public ResponseEntity<List<AdminMailboxAccountDto>> list(){return ResponseEntity.ok(service.list());}
    @PostMapping public ResponseEntity<AdminMailboxAccountDto> create(@Valid @RequestBody AdminMailboxAccountRequestDto request){return ResponseEntity.ok(service.create(request));}
    @PutMapping("/{id}") public ResponseEntity<AdminMailboxAccountDto> update(@PathVariable long id,@Valid @RequestBody AdminMailboxAccountRequestDto request){return ResponseEntity.ok(service.update(id,request));}
    @PostMapping("/{id}/test") public ResponseEntity<AdminMailboxAccountDto> test(@PathVariable long id){return ResponseEntity.ok(service.test(id));}
    @GetMapping("/{id}/messages") public ResponseEntity<List<AdminMailboxMessageDto>> messages(@PathVariable long id,@RequestParam(defaultValue="INBOX") String folder,@RequestParam(defaultValue="50") int limit){return ResponseEntity.ok(service.messages(id,folder,Math.max(1,Math.min(limit,100))));}
    @GetMapping("/{id}/messages/{uid}") public ResponseEntity<AdminMailboxMessageDetailDto> message(@PathVariable long id,@PathVariable long uid,@RequestParam(defaultValue="INBOX") String folder){return ResponseEntity.ok(service.message(id,folder,uid));}
}
