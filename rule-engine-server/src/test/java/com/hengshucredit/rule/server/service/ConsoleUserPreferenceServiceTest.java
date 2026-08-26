package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.server.mapper.ConsoleUserPreferenceMapper;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class ConsoleUserPreferenceServiceTest {

    @Test
    public void saveOverwritesOnlyTheSameUsersSameKey() {
        InMemoryPreferences preferences = new InMemoryPreferences();
        ConsoleUserPreferenceService service = preferences.service();

        service.save(7L, "UI_THEME", "{\"schemaVersion\":1}", "alice");
        service.save(8L, "UI_THEME",
                "{\"schemaVersion\":1,\"colorWeak\":true}", "bob");
        service.save(7L, "UI_THEME",
                "{\"schemaVersion\":1,\"colorWeak\":false}", "alice");

        assertEquals("{\"schemaVersion\":1,\"colorWeak\":false}",
                service.find(7L, "UI_THEME"));
        assertEquals("{\"schemaVersion\":1,\"colorWeak\":true}",
                service.find(8L, "UI_THEME"));
    }

    @Test
    public void deleteRemovesOnlyTheRequestedPreference() {
        InMemoryPreferences preferences = new InMemoryPreferences();
        ConsoleUserPreferenceService service = preferences.service();
        service.save(7L, "UI_THEME", "{}", "alice");
        service.save(7L, "OTHER", "{\"value\":1}", "alice");

        service.delete(7L, "UI_THEME");

        assertNull(service.find(7L, "UI_THEME"));
        assertEquals("{\"value\":1}", service.find(7L, "OTHER"));
    }

    @Test
    public void invalidIdentityKeyAndJsonAreRejectedBeforePersistence() {
        ConsoleUserPreferenceService service = new InMemoryPreferences().service();

        assertThrows(IllegalArgumentException.class,
                () -> service.find(null, "UI_THEME"));
        assertThrows(IllegalArgumentException.class,
                () -> service.find(7L, " "));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "UI_THEME", " ", "alice"));
        assertThrows(IllegalArgumentException.class,
                () -> service.save(7L, "UI_THEME", "{}", " "));
    }

    private static final class InMemoryPreferences {
        private final Map<String, String> values = new HashMap<>();

        private ConsoleUserPreferenceService service() {
            ConsoleUserPreferenceMapper mapper =
                    (ConsoleUserPreferenceMapper) Proxy.newProxyInstance(
                            ConsoleUserPreferenceMapper.class.getClassLoader(),
                            new Class<?>[]{ConsoleUserPreferenceMapper.class},
                            (proxy, method, args) -> {
                                String name = method.getName();
                                if ("findValue".equals(name)) {
                                    return values.get(key(args[0], args[1]));
                                }
                                if ("upsertValue".equals(name)) {
                                    values.put(key(args[0], args[1]),
                                            (String) args[2]);
                                    return 1;
                                }
                                if ("deleteValue".equals(name)) {
                                    return values.remove(key(args[0], args[1])) == null
                                            ? 0 : 1;
                                }
                                if ("toString".equals(name)) {
                                    return "InMemoryConsoleUserPreferenceMapper";
                                }
                                throw new UnsupportedOperationException(name);
                            });
            return new ConsoleUserPreferenceService(mapper);
        }

        private String key(Object userId, Object preferenceKey) {
            return userId + ":" + preferenceKey;
        }
    }
}
