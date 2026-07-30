package com.hengshucredit.rule.server.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import org.junit.Assert;
import org.junit.Test;

public class MybatisPlusConfigTest {

    @Test
    public void versionedEntitiesHaveOptimisticLockerInterceptor() {
        MybatisPlusInterceptor interceptor =
                new MybatisPlusConfig().mybatisPlusInterceptor();

        Assert.assertTrue(interceptor.getInterceptors().stream()
                .anyMatch(OptimisticLockerInnerInterceptor.class::isInstance));
    }
}
