package com.hengshucredit.rule.client.sync;

import com.hengshucredit.rule.client.cache.L1MemoryCache;
import com.hengshucredit.rule.client.function.ClientFunctionRegistrar;
import com.hengshucredit.rule.core.engine.QLExpressEngine;
import org.junit.Test;
import org.springframework.data.redis.listener.Topic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RedisSubscriberTest {

    @Test
    public void subscribesProjectPushChannelAndBroadcastChannel() throws Exception {
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, "credit_project");

        assertEquals("rule:push:credit_project", getField(subscriber, "channel"));
        List<?> topics = (List<?>) getField(subscriber, "topics");
        assertEquals(2, topics.size());
        assertEquals("rule:push:credit_project", ((Topic) topics.get(0)).getTopic());
        assertEquals("rule:push:broadcast", ((Topic) topics.get(1)).getTopic());
    }

    @Test
    public void storesRootOutputScriptNamesFromPublishMessage() throws Exception {
        L1MemoryCache cache = new L1MemoryCache(10);
        RedisSubscriber subscriber = new RedisSubscriber(cache, null, "credit_project");
        Method handleMessage = RedisSubscriber.class.getDeclaredMethod("handleMessage", String.class);
        handleMessage.setAccessible(true);

        handleMessage.invoke(subscriber, "{"
                + "\"action\":\"PUBLISH\","
                + "\"ruleCode\":\"ROOT\","
                + "\"version\":1,"
                + "\"outputScriptNames\":[\"decision\",\"notAssigned\"]"
                + "}");

        assertEquals(Arrays.asList("decision", "notAssigned"),
                cache.get("ROOT").getOutputScriptNames());
    }

    @Test
    public void withoutProjectCodeSubscribesOnlyToGlobalBroadcastChannel() throws Exception {
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, null);

        assertNull(getField(subscriber, "channel"));
        List<?> topics = (List<?>) getField(subscriber, "topics");
        assertEquals(1, topics.size());
        assertEquals("rule:push:broadcast", ((Topic) topics.get(0)).getTopic());
    }

    @Test
    public void ignoresProjectFunctionUpdatesForAnotherProject() throws Exception {
        QLExpressEngine engine = new QLExpressEngine();
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, "project-a");
        subscriber.setFunctionRegistrar(new ClientFunctionRegistrar(engine, null));

        handle(subscriber, "{\"action\":\"FUNC_UPDATE\",\"scope\":\"PROJECT\","
                + "\"projectCode\":\"project-b\",\"funcCode\":\"foreignFn\","
                + "\"funcImplType\":\"SCRIPT\",\"funcImplScript\":\"7\"}");

        assertNull(engine.getRunner().getFunction("foreignFn"));
    }

    @Test
    public void deletesMatchingProjectFunctionThenAllowsScopedReaddition() throws Exception {
        QLExpressEngine engine = new QLExpressEngine();
        RedisSubscriber subscriber = new RedisSubscriber(new L1MemoryCache(10), null, "project-a");
        ClientFunctionRegistrar registrar = new ClientFunctionRegistrar(engine, null, "project-a");
        subscriber.setFunctionRegistrar(registrar);
        handle(subscriber, "{\"action\":\"FUNC_UPDATE\",\"scope\":\"PROJECT\","
                + "\"projectCode\":\"project-a\",\"funcCode\":\"localFn\","
                + "\"funcImplType\":\"SCRIPT\",\"funcImplScript\":\"7\"}");

        handle(subscriber, "{\"action\":\"FUNC_DELETE\",\"scope\":\"PROJECT\","
                + "\"projectCode\":\"project-a\",\"funcCode\":\"localFn\"}");
        assertFalse(registrar.hasRemoteFunction("PROJECT", "project-a", "localFn"));
        handle(subscriber, "{\"action\":\"FUNC_UPDATE\",\"scope\":\"PROJECT\","
                + "\"projectCode\":\"project-a\",\"funcCode\":\"localFn\","
                + "\"funcImplType\":\"SCRIPT\",\"funcImplScript\":\"8\"}");

        assertEquals(8, ((Number) engine.execute("localFn()", java.util.Collections.emptyMap(), false)
                .getResult()).intValue());
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private void handle(RedisSubscriber subscriber, String message) throws Exception {
        Method handleMessage = RedisSubscriber.class.getDeclaredMethod("handleMessage", String.class);
        handleMessage.setAccessible(true);
        handleMessage.invoke(subscriber, message);
    }
}
