package com.linrun.trigger.http.agent;

import com.linrun.api.dto.QuotaStreamEvent;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Service
public class AgentBackgroundStreamService {

    private static final int REPLAY_LIMIT = 500;

    private final Map<String, BackgroundTask> tasks = new ConcurrentHashMap<>();

    public Flux<QuotaStreamEvent<?>> startOrAttach(String taskKey, Supplier<Flux<QuotaStreamEvent<?>>> sourceFactory) {
        BackgroundTask existing = tasks.get(taskKey);
        if (existing != null && existing.isRunning()) {
            return existing.flux();
        }

        BackgroundTask task = new BackgroundTask();
        BackgroundTask previous = tasks.putIfAbsent(taskKey, task);
        if (previous != null && previous.isRunning()) {
            return previous.flux();
        }
        if (previous != null && !tasks.replace(taskKey, previous, task)) {
            return startOrAttach(taskKey, sourceFactory);
        }

        task.start(sourceFactory.get()
                .doOnNext(task::emit)
                .doOnError(task::error)
                .doFinally(signalType -> {
                    task.complete();
                    tasks.remove(taskKey, task);
                })
                .subscribe());
        return task.flux();
    }

    public Flux<QuotaStreamEvent<?>> attach(String taskKey) {
        BackgroundTask task = tasks.get(taskKey);
        if (task == null || !task.isRunning()) {
            return Flux.empty();
        }
        return task.flux();
    }

    public boolean isRunning(String taskKey) {
        BackgroundTask task = tasks.get(taskKey);
        return task != null && task.isRunning();
    }

    private static class BackgroundTask {
        private final Sinks.Many<QuotaStreamEvent<?>> sink = Sinks.many().replay().limit(REPLAY_LIMIT);
        private final AtomicBoolean running = new AtomicBoolean(true);
        private Disposable disposable;

        void start(Disposable disposable) {
            this.disposable = disposable;
        }

        void emit(QuotaStreamEvent<?> event) {
            sink.tryEmitNext(event);
        }

        void error(Throwable error) {
            sink.tryEmitError(error);
        }

        void complete() {
            if (running.compareAndSet(true, false)) {
                sink.tryEmitComplete();
            }
        }

        boolean isRunning() {
            return running.get() && (disposable == null || !disposable.isDisposed());
        }

        Flux<QuotaStreamEvent<?>> flux() {
            return sink.asFlux();
        }
    }
}















