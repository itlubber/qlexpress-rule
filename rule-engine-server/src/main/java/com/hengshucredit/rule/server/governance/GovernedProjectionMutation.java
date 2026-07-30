package com.hengshucredit.rule.server.governance;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks legacy management endpoints that write effective projection tables
 * directly. External callers must use the governance draft API instead.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface GovernedProjectionMutation {
}
