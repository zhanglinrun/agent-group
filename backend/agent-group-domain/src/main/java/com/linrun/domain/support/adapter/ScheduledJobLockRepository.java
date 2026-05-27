package com.linrun.domain.support.adapter;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface ScheduledJobLockRepository {

    Optional<String> tryLock(String lockName, Duration leaseTime);

    void unlock(String lockName, String lockToken);

    static ScheduledJobLockRepository noop() {
        return NoopScheduledJobLockRepository.INSTANCE;
    }

    class NoopScheduledJobLockRepository implements ScheduledJobLockRepository {

        private static final NoopScheduledJobLockRepository INSTANCE = new NoopScheduledJobLockRepository();

        @Override
        public Optional<String> tryLock(String lockName, Duration leaseTime) {
            return Optional.of(UUID.randomUUID().toString());
        }

        @Override
        public void unlock(String lockName, String lockToken) {
        }
    }
}
