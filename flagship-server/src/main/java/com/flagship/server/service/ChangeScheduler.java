package com.flagship.server.service;

import java.time.Instant;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Every second, applies the scheduled changes whose time has come. */
@Component
public class ChangeScheduler {

    private static final System.Logger LOG = System.getLogger(ChangeScheduler.class.getName());

    private final ChangeRequestService service;

    public ChangeScheduler(ChangeRequestService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${flagship.scheduler.interval:1000}")
    public void applyDueChanges() {
        for (Long id : service.dueIds(Instant.now())) {
            try {
                service.applyScheduled(id);
            } catch (ObjectOptimisticLockingFailureException e) {
                // someone cancelled it at the same moment: nothing to do
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "FlagShip: scheduled change {0} could not be applied: {1}", id, e.getMessage());
                try {
                    service.markFailed(id, e.getMessage());
                } catch (RuntimeException ignored) {
                    // it will be tried again on the next round
                }
            }
        }
    }
}