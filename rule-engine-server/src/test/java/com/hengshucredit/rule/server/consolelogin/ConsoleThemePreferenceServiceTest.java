package com.hengshucredit.rule.server.consolelogin;

import com.hengshucredit.rule.model.dto.ConsoleThemePreference;
import com.hengshucredit.rule.server.mapper.ConsoleUserPreferenceMapper;
import com.hengshucredit.rule.server.service.ConsoleUserPreferenceService;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ConsoleThemePreferenceServiceTest {

    @Test
    public void missingPreferenceUsesTheCompleteSystemDefault() {
        ConsoleThemePreference theme = service(new HashMap<>()).load(7L);

        assertEquals(Integer.valueOf(1), theme.getSchemaVersion());
        assertEquals("LIGHT", theme.getColorScheme());
        assertEquals("THEME_BLUE", theme.getAccentPreset());
        assertEquals("DARK", theme.getSidebarTheme());
        assertEquals("FLUID", theme.getContentWidth());
        assertTrue(theme.getFixedSidebar());
        assertFalse(theme.getColorWeak());
    }

    @Test
    public void validGradientThemeIsNormalizedSavedAndReloaded() {
        Map<String, String> rows = new HashMap<>();
        ConsoleThemePreferenceService service = service(rows);
        Map<String, Object> request = validTheme();
        request.put("colorScheme", "DARK");
        request.put("accentPreset", "PEACH_PINK_GRADIENT");

        ConsoleThemePreference saved = service.save(7L, "alice", request);
        ConsoleThemePreference loaded = service.load(7L);

        assertEquals("DARK", saved.getColorScheme());
        assertEquals("PEACH_PINK_GRADIENT", loaded.getAccentPreset());
        assertTrue(rows.get("7:UI_THEME").contains("PEACH_PINK_GRADIENT"));
    }

    @Test
    public void unknownFieldsAndUnlistedAccentPresetsAreRejected() {
        ConsoleThemePreferenceService service = service(new HashMap<>());
        Map<String, Object> invalidPreset = validTheme();
        invalidPreset.put("accentPreset", "url(evil)");
        Map<String, Object> unknownField = validTheme();
        unknownField.put("unknown", true);

        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", invalidPreset));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", unknownField));
    }

    @Test
    public void missingFieldsWrongTypesAndFutureVersionsAreRejected() {
        ConsoleThemePreferenceService service = service(new HashMap<>());
        Map<String, Object> missingField = validTheme();
        missingField.remove("colorWeak");
        Map<String, Object> wrongType = validTheme();
        wrongType.put("fixedSidebar", "true");
        Map<String, Object> futureVersion = validTheme();
        futureVersion.put("schemaVersion", 2);

        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", missingField));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", wrongType));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "alice", futureVersion));
    }

    @Test
    public void corruptStoredJsonFallsBackWithoutBlockingTheUser() {
        Map<String, String> rows = new HashMap<>();
        rows.put("7:UI_THEME", "not-json");

        ConsoleThemePreference loaded = service(rows).load(7L);

        assertEquals("LIGHT", loaded.getColorScheme());
        assertEquals("THEME_BLUE", loaded.getAccentPreset());
    }

    private static Map<String, Object> validTheme() {
        Map<String, Object> value = new HashMap<>();
        value.put("schemaVersion", 1);
        value.put("colorScheme", "LIGHT");
        value.put("accentPreset", "THEME_BLUE");
        value.put("sidebarTheme", "DARK");
        value.put("contentWidth", "FLUID");
        value.put("fixedSidebar", true);
        value.put("colorWeak", false);
        return value;
    }

    private static ConsoleThemePreferenceService service(
            Map<String, String> rows) {
        ConsoleUserPreferenceMapper mapper =
                (ConsoleUserPreferenceMapper) Proxy.newProxyInstance(
                        ConsoleUserPreferenceMapper.class.getClassLoader(),
                        new Class<?>[]{ConsoleUserPreferenceMapper.class},
                        (proxy, method, args) -> {
                            if ("findValue".equals(method.getName())) {
                                return rows.get(key(args[0], args[1]));
                            }
                            if ("upsertValue".equals(method.getName())) {
                                rows.put(key(args[0], args[1]), (String) args[2]);
                                return 1;
                            }
                            if ("deleteValue".equals(method.getName())) {
                                return rows.remove(key(args[0], args[1])) == null
                                        ? 0 : 1;
                            }
                            if ("toString".equals(method.getName())) {
                                return "InMemoryConsoleUserPreferenceMapper";
                            }
                            throw new UnsupportedOperationException(method.getName());
                        });
        return new ConsoleThemePreferenceService(
                new ConsoleUserPreferenceService(mapper));
    }

    private static String key(Object userId, Object preferenceKey) {
        return userId + ":" + preferenceKey;
    }
}
