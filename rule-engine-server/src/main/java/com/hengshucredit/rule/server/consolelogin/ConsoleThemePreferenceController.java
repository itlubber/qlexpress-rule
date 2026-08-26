package com.hengshucredit.rule.server.consolelogin;

import com.hengshucredit.rule.model.dto.ConsoleThemePreference;
import com.hengshucredit.rule.server.common.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/console/preferences")
public class ConsoleThemePreferenceController {

    private final RuleEngineConsoleLoginProperties consoleLoginProperties;
    private final ConsoleThemePreferenceService themePreferenceService;

    public ConsoleThemePreferenceController(
            RuleEngineConsoleLoginProperties consoleLoginProperties,
            ConsoleThemePreferenceService themePreferenceService) {
        this.consoleLoginProperties = consoleLoginProperties;
        this.themePreferenceService = themePreferenceService;
    }

    @GetMapping("/theme")
    public R<ConsoleThemePreference> theme(HttpServletRequest request) {
        SessionIdentity identity = currentIdentity(request);
        if (identity == null) return R.fail(401, "未登录");
        return R.ok(themePreferenceService.load(identity.userId()));
    }

    @PutMapping("/theme")
    public R<ConsoleThemePreference> saveTheme(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        SessionIdentity identity = currentIdentity(request);
        if (identity == null) return R.fail(401, "未登录");
        try {
            return R.ok(themePreferenceService.save(
                    identity.userId(), identity.username(), body));
        } catch (IllegalArgumentException exception) {
            return R.fail(400, exception.getMessage());
        }
    }

    private SessionIdentity currentIdentity(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object userId = session.getAttribute(
                consoleLoginProperties.getSessionUserIdAttribute());
        Object username = session.getAttribute(
                consoleLoginProperties.getSessionUsernameAttribute());
        if (!(userId instanceof Number) || username == null
                || username.toString().isBlank()) {
            return null;
        }
        return new SessionIdentity(
                ((Number) userId).longValue(), username.toString());
    }

    private record SessionIdentity(Long userId, String username) {
    }
}
