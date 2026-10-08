package com.dispatch.device;

import java.net.URI;
import java.util.List;

import com.dispatch.common.web.PageResponse;
import com.dispatch.common.web.Paging;
import com.dispatch.device.dto.DeviceRequest;
import com.dispatch.device.dto.DeviceResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceRegistry registry;

    public DeviceController(DeviceRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public PageResponse<DeviceResponse> search(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(name = "status", required = false) List<DeviceStatus> statuses,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return registry.search(serviceId, statuses, query, Paging.page(page), Paging.size(size));
    }

    @GetMapping("/{id}")
    public DeviceResponse get(@PathVariable Long id) {
        return registry.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceResponse> create(@Valid @RequestBody DeviceRequest request) {
        DeviceResponse created = registry.create(request);
        return ResponseEntity.created(URI.create("/api/v1/devices/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public DeviceResponse update(@PathVariable Long id, @Valid @RequestBody DeviceRequest request) {
        return registry.update(id, request);
    }
}
