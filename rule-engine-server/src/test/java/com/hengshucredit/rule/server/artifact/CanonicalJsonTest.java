package com.hengshucredit.rule.server.artifact;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class CanonicalJsonTest {

    @Test
    public void writesJavaTimeValuesAsStableIsoText() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("compileTime",
                LocalDateTime.of(2026, 7, 30, 11, 30, 45));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", "rule");
        snapshot.put("content", content);

        String expected = "{\"content\":{\"compileTime\":"
                + "\"2026-07-30T11:30:45\"},\"name\":\"rule\"}";

        Assert.assertEquals(expected, CanonicalJson.write(snapshot));
        Assert.assertEquals(expected, new String(
                CanonicalJson.writeBytes(snapshot),
                StandardCharsets.UTF_8));
    }
}
