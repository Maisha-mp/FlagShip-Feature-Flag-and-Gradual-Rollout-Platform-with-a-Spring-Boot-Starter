package com.flagship.server.api;

import com.flagship.server.service.SegmentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reading needs VIEWER, changing needs EDITOR (see ApiAuthorization). */
@RestController
@RequestMapping("/api/projects/{projectKey}/segments")
public class SegmentController {

    private final SegmentService service;

    public SegmentController(SegmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<SegmentResponse> list(@PathVariable String projectKey) {
        return service.list(projectKey);
    }

    @GetMapping("/{key}")
    public SegmentResponse get(@PathVariable String projectKey, @PathVariable String key) {
        return service.get(projectKey, key);
    }

    /** Creates the segment or replaces it. */
    @PutMapping("/{key}")
    public SegmentResponse put(@PathVariable String projectKey, @PathVariable String key,
                               @Valid @RequestBody SegmentRequest request) {
        return service.put(projectKey, key, request);
    }

    @DeleteMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String projectKey, @PathVariable String key) {
        service.delete(projectKey, key);
    }
}