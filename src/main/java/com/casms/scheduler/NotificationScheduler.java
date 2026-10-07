package com.casms.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    // Example cron job
    // @Scheduled(cron = "0 0 * * * *")
    public void execute() {
        log.debug("Executing NotificationScheduler");
        // TODO: invoke business service
    }
}
