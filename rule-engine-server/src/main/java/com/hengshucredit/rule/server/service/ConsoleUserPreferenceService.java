package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.mapper.ConsoleUserPreferenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsoleUserPreferenceService {

    private final ConsoleUserPreferenceMapper preferenceMapper;

    public ConsoleUserPreferenceService(
            ConsoleUserPreferenceMapper preferenceMapper) {
        this.preferenceMapper = preferenceMapper;
    }

    public String find(Long userId, String preferenceKey) {
        requireUserId(userId);
        String key = requireText(preferenceKey, "偏好键不能为空");
        return preferenceMapper.findValue(userId, key);
    }

    @Transactional
    public void save(Long userId, String preferenceKey,
                     String preferenceValue, String operator) {
        requireUserId(userId);
        String key = requireText(preferenceKey, "偏好键不能为空");
        String value = requireText(preferenceValue, "偏好内容不能为空");
        String username = requireText(operator, "操作人不能为空");
        preferenceMapper.upsertValue(userId, key, value, username);
    }

    @Transactional
    public void delete(Long userId, String preferenceKey) {
        requireUserId(userId);
        String key = requireText(preferenceKey, "偏好键不能为空");
        preferenceMapper.deleteValue(userId, key);
    }

    private static void requireUserId(Long userId) {
        if (userId == null || userId <= 0L) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
