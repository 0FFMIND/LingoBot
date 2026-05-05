package com.lingobot.core.user.preference.service;

import com.lingobot.core.user.preference.dto.UpdateUserPreferenceRequest;
import com.lingobot.core.user.preference.dto.UserPreferenceDTO;

public interface UserPreferenceService {
    
    UserPreferenceDTO getOrCreatePreference(Long userId);
    
    UserPreferenceDTO getPreference(Long userId);
    
    UserPreferenceDTO updatePreference(Long userId, UpdateUserPreferenceRequest request);
    
    void evictCache(Long userId);
}
