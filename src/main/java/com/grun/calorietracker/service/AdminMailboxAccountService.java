package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminMailboxAccountDto;
import com.grun.calorietracker.dto.AdminMailboxAccountRequestDto;
import com.grun.calorietracker.dto.AdminMailboxMessageDto;
import com.grun.calorietracker.dto.AdminMailboxMessageDetailDto;
import java.util.List;

public interface AdminMailboxAccountService {
    List<AdminMailboxAccountDto> list();
    AdminMailboxAccountDto create(AdminMailboxAccountRequestDto request);
    AdminMailboxAccountDto update(long id,AdminMailboxAccountRequestDto request);
    AdminMailboxAccountDto test(long id);
    List<AdminMailboxMessageDto> messages(long id, String folder, int limit);
    AdminMailboxMessageDetailDto message(long id, String folder, long uid);
}
