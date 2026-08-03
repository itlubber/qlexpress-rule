package com.hengshucredit.rule.server.governance;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hengshucredit.rule.model.dto.GovernanceApprovalQuery;
import com.hengshucredit.rule.model.entity.GovernanceApprovalRequest;
import com.hengshucredit.rule.server.mapper.GovernanceApprovalRequestMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class GovernanceApprovalTaskViewTest {

    private GovernanceApprovalRequestMapper mapper;
    private GovernanceApprovalService service;
    private LambdaQueryWrapper<GovernanceApprovalRequest> capturedWrapper;
    private int selectCountIndex;
    private final List<Long> countResults = List.of(4L, 2L, 7L, 11L);

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new Configuration(), ""),
                GovernanceApprovalRequest.class);
        capturedWrapper = null;
        selectCountIndex = 0;
        mapper = (GovernanceApprovalRequestMapper) Proxy.newProxyInstance(
                GovernanceApprovalRequestMapper.class.getClassLoader(),
                new Class<?>[]{GovernanceApprovalRequestMapper.class},
                (proxy, method, args) -> {
                    if ("selectPage".equals(method.getName())) {
                        capturedWrapper = args == null ? null
                                : (LambdaQueryWrapper<
                                GovernanceApprovalRequest>) args[1];
                        IPage<GovernanceApprovalRequest> page =
                                (IPage<GovernanceApprovalRequest>) args[0];
                        page.setRecords(List.of());
                        page.setTotal(0L);
                        return page;
                    }
                    if ("selectCount".equals(method.getName())) {
                        capturedWrapper = (LambdaQueryWrapper<
                                GovernanceApprovalRequest>) args[0];
                        return countResults.get(selectCountIndex++);
                    }
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(this, args);
                    }
                    return null;
                });
        service = new GovernanceApprovalService();
        ReflectionTestUtils.setField(service, "requestMapper", mapper);
    }

    @Test
    public void pendingScopeShowsPendingAcrossEveryResourceType() {
        GovernanceApprovalQuery query = new GovernanceApprovalQuery();
        query.setTaskScope("PENDING");

        service.page(query, "reviewer-a");

        String sql = capturedWrapper.getCustomSqlSegment();
        Map<String, Object> params = capturedWrapper
                .getParamNameValuePairs();
        Assert.assertTrue(params.containsValue("PENDING"));
        Assert.assertFalse(params.containsValue("reviewer-a"));
        Assert.assertFalse(sql.contains("resource_type"));
    }

    @Test
    public void mineScopeIsResolvedOnServerAndKeepsStatusFilter() {
        GovernanceApprovalQuery query = new GovernanceApprovalQuery();
        query.setTaskScope("MINE");
        query.setStatus("EDITING");

        service.page(query, "applicant-a");

        capturedWrapper.getCustomSqlSegment();
        Collection<Object> values = capturedWrapper
                .getParamNameValuePairs().values();
        Assert.assertTrue(values.contains("applicant-a"));
        Assert.assertTrue(values.contains("EDITING"));
    }

    @Test
    public void completedScopeIncludesEveryTerminalOutcome() {
        GovernanceApprovalQuery query = new GovernanceApprovalQuery();
        query.setTaskScope("COMPLETED");

        service.page(query, "reviewer-a");

        capturedWrapper.getCustomSqlSegment();
        Collection<Object> values = capturedWrapper
                .getParamNameValuePairs().values();
        Assert.assertTrue(values.contains("APPROVED"));
        Assert.assertTrue(values.contains("REJECTED"));
        Assert.assertTrue(values.contains("CONFLICT"));
        Assert.assertTrue(values.contains("CANCELLED"));
    }

    @Test
    public void summaryReturnsActionableCountsForCurrentOperator() {
        GovernanceApprovalSummary summary =
                service.summary(9L, "applicant-a");

        Assert.assertEquals(4L, summary.pendingCount());
        Assert.assertEquals(2L, summary.myDraftCount());
        Assert.assertEquals(7L, summary.myRequestCount());
        Assert.assertEquals(11L, summary.completedCount());
        Assert.assertEquals(4, selectCountIndex);
    }
}
