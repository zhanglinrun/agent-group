package com.linrun.trigger.service;

import com.linrun.domain.support.adapter.ScheduledJobLockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class ScheduledJobLockExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledJobLockExecutor.class);

    private final ScheduledJobLockRepository scheduledJobLockRepository;

    public ScheduledJobLockExecutor(ScheduledJobLockRepository scheduledJobLockRepository) {
        this.scheduledJobLockRepository = scheduledJobLockRepository;
    }

    public boolean execute(String lockName, Duration leaseTime, Runnable task) {
        Optional<String> lockToken = scheduledJobLockRepository.tryLock(lockName, leaseTime);
        if (lockToken.isEmpty()) {
            LOGGER.info("scheduled job skipped because lock is held, lockName={}", lockName);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            scheduledJobLockRepository.unlock(lockName, lockToken.get());
        }
    }
}
