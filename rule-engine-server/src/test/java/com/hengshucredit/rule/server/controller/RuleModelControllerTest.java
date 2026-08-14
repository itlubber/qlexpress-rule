package com.hengshucredit.rule.server.controller;

import com.hengshucredit.rule.server.common.R;
import com.hengshucredit.rule.server.service.RuleModelService;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RuleModelControllerTest {

    @Test
    public void returnsOnnxRuntimeCapabilitiesForModelConfigurationPage() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("onnxRuntimeVersion", "1.26.0");
        capabilities.put("availableProviders", Arrays.asList("CPU", "CUDA"));
        capabilities.put("cudaAvailable", true);
        capabilities.put("modelRuntimeStates", Collections.singletonList(
                Collections.singletonMap("modelId", 7L)));
        RuleModelService service = new RuleModelService() {
            @Override
            public Map<String, Object> runtimeCapabilities() {
                return capabilities;
            }
        };
        RuleModelController controller = new RuleModelController();
        ReflectionTestUtils.setField(controller, "modelService", service);

        R<Map<String, Object>> response = controller.runtimeCapabilities();

        assertEquals(200, response.getCode());
        assertEquals("1.26.0", response.getData().get("onnxRuntimeVersion"));
        assertTrue((Boolean) response.getData().get("cudaAvailable"));
        assertEquals(7L, ((Map<?, ?>) ((Iterable<?>) response.getData()
                .get("modelRuntimeStates")).iterator().next()).get("modelId"));
    }
}
