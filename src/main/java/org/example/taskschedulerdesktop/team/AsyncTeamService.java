package org.example.taskschedulerdesktop.team;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Вызовы {@link TeamService} в фоне. Колбэки выполняются в UI-потоке.
 */
public class AsyncTeamService {

    private static final Logger log = LoggerFactory.getLogger(AsyncTeamService.class);

    private final TeamService delegate;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    });

    public AsyncTeamService(TeamService delegate) {
        this.delegate = delegate;
    }

    public void findTeam(Consumer<List<TeamMember>> onSuccess, Consumer<Throwable> onError) {
        Task<List<TeamMember>> task = new Task<>() {
            @Override
            protected List<TeamMember> call() {
                return delegate.findTeam();
            }
        };

        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> {
            log.error("Team loading failed", task.getException());
            onError.accept(task.getException());
        });

        executor.submit(task);
    }
}
