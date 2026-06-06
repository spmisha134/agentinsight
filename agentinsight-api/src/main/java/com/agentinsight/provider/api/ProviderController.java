package com.agentinsight.provider.api;

import com.agentinsight.provider.model.ProviderDescriptor;
import com.agentinsight.provider.model.ProviderDiscoveryResult;
import com.agentinsight.provider.model.ProviderHealth;
import com.agentinsight.provider.model.ProviderInstance;
import com.agentinsight.provider.model.ProviderSelectionRequest;
import com.agentinsight.provider.service.ProviderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
@CrossOrigin(origins = "*")
public class ProviderController {
    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    public List<ProviderDescriptor> providers() {
        return providerService.registry();
    }

    @GetMapping("/active")
    public ProviderInstance active() {
        return providerService.activeProvider().orElse(null);
    }

    @GetMapping("/discover")
    public List<ProviderDiscoveryResult> discover() {
        return providerService.discover();
    }

    @PostMapping("/validate")
    public ProviderHealth validate(@Valid @RequestBody ProviderSelectionRequest request) {
        return providerService.validate(request);
    }

    @PostMapping("/active")
    public ProviderInstance setActive(@Valid @RequestBody ProviderSelectionRequest request) {
        return providerService.setActive(request);
    }
}
