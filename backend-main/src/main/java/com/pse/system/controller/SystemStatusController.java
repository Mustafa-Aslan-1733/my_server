package com.pse.system.controller;

import com.pse.system.dto.SystemStatusResponse;
import com.pse.system.service.SystemStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides SystemStatusController.
 */
@RestController
@RequestMapping({"/admin/system", "/system"})
public class SystemStatusController {

    private final SystemStatusService service;

    /**
     * Creates SystemStatusController.
     *
     * @param service the service
     */
    public SystemStatusController(SystemStatusService service) {
        this.service = service;
    }

    /**
     * Returns getStatus.
     *
     * @return the result
     */
    @GetMapping("/status")
    public SystemStatusResponse getStatus() {
        return service.getStatus();
    }
}
