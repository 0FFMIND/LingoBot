package com.lingobot.userpreference.service;

import com.lingobot.userpreference.dto.UpdateUserPreferenceRequest;
import com.lingobot.userpreference.dto.UserPreferenceDTO;

public interface UserPreferenceService {
    
    UserPreferenceDTO getOrCreatePreference(Long userId);
    
    UserPreferenceDTO getPreference(Long userId);
    
    UserPreferenceDTO updatePreference(Long userId, UpdateUserPreferenceRequest request);
    
    void evictCache(Long userId);
}
