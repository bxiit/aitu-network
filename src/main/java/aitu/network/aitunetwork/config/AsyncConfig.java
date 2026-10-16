package aitu.network.aitunetwork.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * Real-time message and event fan-out. Kept separate from slow blocking work (mail) so an SMTP backlog can
     * never starve chat delivery. Under extreme load the caller runs the task instead of it being rejected and
     * silently dropped, which applies backpressure rather than losing messages.
     */
    @Bean("wsExecutor")
    public Executor wsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("ws-fanout-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }

    /**
     * General blocking background work: registration (bcrypt + save) and e-mail (SMTP). Also never drops tasks;
     * a saturated pool makes the caller wait instead, so a confirmation e-mail is delayed, never lost.
     */
    @Bean("appExecutor")
    public Executor appExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("app-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
