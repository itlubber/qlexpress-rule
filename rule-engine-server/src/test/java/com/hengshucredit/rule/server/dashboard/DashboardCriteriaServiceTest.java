package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

public class DashboardCriteriaServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-26T04:34:56Z"), ZONE);

    @Test
    public void missingTimesUseCurrentDayAndPreviousSixWholeDays() {
        DashboardCriteria criteria = service(null).resolve(null, null, null);

        assertEquals(LocalDateTime.of(2026, 8, 20, 0, 0),
                criteria.startTime());
        assertEquals(LocalDateTime.of(2026, 8, 26, 12, 34, 56),
                criteria.endTime());
    }

    @Test
    public void rangesLongerThanNinetyDaysAreRejected() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(null).resolve(null, start,
                        start.plusDays(90).plusSeconds(1)));

        assertEquals("统计时间跨度不能超过90天", error.getMessage());
    }

    @Test
    public void projectIdResolvesStableCodeAndMissingProjectIsRejected() {
        RuleProject project = new RuleProject();
        project.setId(9L);
        project.setProjectCode("P-009");

        DashboardCriteria criteria = service(project).resolve(
                9L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 2, 0, 0));

        assertEquals(Long.valueOf(9L), criteria.projectId());
        assertEquals("P-009", criteria.projectCode());
        assertThrows(IllegalArgumentException.class,
                () -> service(null).resolve(9L,
                        LocalDateTime.of(2026, 8, 1, 0, 0),
                        LocalDateTime.of(2026, 8, 2, 0, 0)));
    }

    @Test
    public void ruleFiltersAreTrimmedAndBlankValuesBecomeNull() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 2, 0, 0);

        DashboardCriteria criteria = service(null).resolve(
                null, " RC_RISK ", " 风险审批 ", start, end);
        DashboardCriteria blank = service(null).resolve(
                null, "  ", null, start, end);

        assertEquals("RC_RISK", criteria.ruleCode());
        assertEquals("风险审批", criteria.ruleName());
        assertEquals(true, criteria.hasRuleFilter());
        assertEquals(null, blank.ruleCode());
        assertEquals(null, blank.ruleName());
        assertEquals(false, blank.hasRuleFilter());
    }

    @Test
    public void productionConstructorCanBeAutowiredBySpring() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            RuleProjectMapper mapper = (RuleProjectMapper) Proxy.newProxyInstance(
                    RuleProjectMapper.class.getClassLoader(),
                    new Class<?>[]{RuleProjectMapper.class},
                    (proxy, method, args) -> null);
            context.registerBean(RuleProjectMapper.class, () -> mapper);
            context.registerBean(DashboardCriteriaService.class);

            context.refresh();

            assertNotNull(context.getBean(DashboardCriteriaService.class));
        }
    }

    private DashboardCriteriaService service(RuleProject project) {
        RuleProjectMapper mapper = (RuleProjectMapper) Proxy.newProxyInstance(
                RuleProjectMapper.class.getClassLoader(),
                new Class<?>[]{RuleProjectMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) return project;
                    if ("toString".equals(method.getName())) {
                        return "DashboardCriteriaRuleProjectMapper";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new DashboardCriteriaService(mapper, CLOCK);
    }
}
