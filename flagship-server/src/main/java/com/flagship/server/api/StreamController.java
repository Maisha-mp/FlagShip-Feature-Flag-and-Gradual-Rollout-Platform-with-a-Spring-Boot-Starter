package com.flagship.server.api;

import com.flagship.core.model.Environment;
import com.flagship.server.push.ChangeBroadcaster;
import com.flagship.server.push.ChangeSink;
import com.flagship.server.repository.ProjectRepository;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/projects/{projectKey}")
public class StreamController {

    private final ChangeBroadcaster broadcaster;
    private final ProjectRepository projects;

    public StreamController(ChangeBroadcaster broadcaster, ProjectRepository projects) {
        this.broadcaster = broadcaster;
        this.projects = projects;
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String projectKey, @RequestParam("env") Environment env) {
        if (!projects.existsByKey(projectKey)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No project with key '" + projectKey + "'");
        }

        SseEmitter emitter = new SseEmitter(0L);   // 0 = never time out; the heartbeat detects dead clients
        Runnable unregister = broadcaster.register(projectKey, env, new EmitterSink(emitter));
        emitter.onCompletion(unregister);
        emitter.onTimeout(unregister);
        emitter.onError(e -> unregister.run());
        return emitter;
    }

    /** Writes events to one open HTTP connection. */
    private static final class EmitterSink implements ChangeSink {

        private final SseEmitter emitter;

        EmitterSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public synchronized boolean send(String eventName, String json) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
                return true;
            } catch (IOException | IllegalStateException e) {
                return false;
            }
        }

        @Override
        public synchronized boolean ping() {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
                return true;
            } catch (IOException | IllegalStateException e) {
                return false;
            }
        }
    }
}