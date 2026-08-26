package com.hengshucredit.rule.server.dashboard;

import com.hengshucredit.rule.model.entity.RuleProject;
import com.hengshucredit.rule.server.mapper.RuleProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class DashboardCriteriaService {

    private static final Duration MAX_RANGE = Duration.ofDays(90);

    private final RuleProjectMapper projectMapper;
    private final Clock clock;

    @Autowired
    public DashboardCriteriaService(RuleProjectMapper projectMapper) {
        this(projectMapper, Clock.systemDefaultZone());
    }

    DashboardCriteriaService(RuleProjectMapper projectMapper, Clock clock) {
        this.projectMapper = projectMapper;
        this.clock = clock;
    }

    public DashboardCriteria resolve(Long projectId,
                                     LocalDateTime startTime,
                                     LocalDateTime endTime) {
        return resolve(projectId, null, null, startTime, endTime);
    }

    public DashboardCriteria resolve(Long projectId,
                                     String ruleCode,
                                     String ruleName,
                                     LocalDateTime startTime,
                                     LocalDateTime endTime) {
        if ((startTime == null) != (endTime == null)) {
            throw new IllegalArgumentException("开始时间和结束时间必须同时提供");
        }
        if (startTime == null) {
            endTime = LocalDateTime.now(clock);
            startTime = endTime.toLocalDate().minusDays(6).atStartOfDay();
        }
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        if (Duration.between(startTime, endTime).compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("统计时间跨度不能超过90天");
        }

        String projectCode = null;
        if (projectId != null) {
            if (projectId <= 0L) {
                throw new IllegalArgumentException("项目ID无效");
            }
            RuleProject project = projectMapper.selectById(projectId);
            if (project == null) {
                throw new IllegalArgumentException("项目不存在");
            }
            projectCode = project.getProjectCode();
        }
        return new DashboardCriteria(projectId, projectCode,
                normalize(ruleCode, 128, "规则编码"),
                normalize(ruleName, 256, "规则名称"),
                startTime, endTime);
    }

    private String normalize(String value, int maxLength, String label) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "不能超过"
                    + maxLength + "个字符");
        }
        return normalized;
    }
}
