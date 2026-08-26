package com.hengshucredit.rule.server.consolelogin;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hengshucredit.rule.model.dto.ConsoleThemePreference;
import com.hengshucredit.rule.server.service.ConsoleUserPreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class ConsoleThemePreferenceService {

    public static final String PREFERENCE_KEY = "UI_THEME";

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ConsoleThemePreferenceService.class);
    private static final Set<String> FIELDS = Set.of(
            "schemaVersion", "colorScheme", "accentPreset",
            "sidebarTheme", "contentWidth", "fixedSidebar", "colorWeak");
    private static final Set<String> COLOR_SCHEMES = Set.of("LIGHT", "DARK");
    private static final Set<String> SIDEBAR_THEMES = Set.of("LIGHT", "DARK");
    private static final Set<String> CONTENT_WIDTHS = Set.of("FLUID", "FIXED");
    private static final Set<String> ACCENT_PRESETS = Set.of(
            "THEME_BLUE", "LIQUID_PURPLE", "PEACH_PINK", "NEON_PINK",
            "MIDNIGHT_PURPLE", "INDIGO_PURPLE", "NEBULA_BLUE", "CRYSTAL_BLUE",
            "THEME_BLUE_GRADIENT", "LIQUID_PURPLE_GRADIENT",
            "PEACH_PINK_GRADIENT", "NEBULA_GRADIENT");

    private final ConsoleUserPreferenceService preferenceService;

    public ConsoleThemePreferenceService(
            ConsoleUserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    public ConsoleThemePreference defaults() {
        return new ConsoleThemePreference(
                1, "LIGHT", "THEME_BLUE", "DARK", "FLUID", true, false);
    }

    public ConsoleThemePreference load(Long userId) {
        String stored = preferenceService.find(userId, PREFERENCE_KEY);
        if (stored == null || stored.isBlank()) return defaults();
        try {
            JSONObject parsed = JSON.parseObject(stored);
            if (parsed == null) return defaults();
            return validate(parsed);
        } catch (RuntimeException exception) {
            LOGGER.warn("用户 {} 的主题偏好无效，已回退默认配置: {}",
                    userId, exception.getMessage());
            return defaults();
        }
    }

    public ConsoleThemePreference save(Long userId, String username,
                                       Map<String, Object> body) {
        ConsoleThemePreference preference = validate(body);
        preferenceService.save(userId, PREFERENCE_KEY,
                JSON.toJSONString(preference), username);
        return preference;
    }

    private ConsoleThemePreference validate(Map<String, Object> body) {
        if (body == null) {
            throw new IllegalArgumentException("主题配置不能为空");
        }
        if (!body.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException("主题配置字段不完整或包含未知字段");
        }
        Object schemaVersion = body.get("schemaVersion");
        if (!(schemaVersion instanceof Number)
                || ((Number) schemaVersion).intValue() != 1) {
            throw new IllegalArgumentException("不支持的主题配置版本");
        }
        String colorScheme = requireAllowed(body, "colorScheme",
                COLOR_SCHEMES, "整体风格无效");
        String accentPreset = requireAllowed(body, "accentPreset",
                ACCENT_PRESETS, "主题色无效");
        String sidebarTheme = requireAllowed(body, "sidebarTheme",
                SIDEBAR_THEMES, "侧栏风格无效");
        String contentWidth = requireAllowed(body, "contentWidth",
                CONTENT_WIDTHS, "内容区宽度无效");
        Boolean fixedSidebar = requireBoolean(body, "fixedSidebar");
        Boolean colorWeak = requireBoolean(body, "colorWeak");
        return new ConsoleThemePreference(1, colorScheme, accentPreset,
                sidebarTheme, contentWidth, fixedSidebar, colorWeak);
    }

    private static String requireAllowed(Map<String, Object> body, String key,
                                         Set<String> allowed, String message) {
        Object value = body.get(key);
        if (!(value instanceof String) || !allowed.contains(value)) {
            throw new IllegalArgumentException(message);
        }
        return (String) value;
    }

    private static Boolean requireBoolean(Map<String, Object> body,
                                          String key) {
        Object value = body.get(key);
        if (!(value instanceof Boolean)) {
            throw new IllegalArgumentException(key + " 必须为布尔值");
        }
        return (Boolean) value;
    }
}
