package com.hengshucredit.rule.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsoleThemePreference {
    private Integer schemaVersion;
    private String colorScheme;
    private String accentPreset;
    private String sidebarTheme;
    private String contentWidth;
    private Boolean fixedSidebar;
    private Boolean colorWeak;
}
