package io.hephaistos.observarium.handler;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ObservariumExceptionHandlerTest {

    // Track Observarium instances created so we can shut them down after each test.
    private final List<Observarium> created = new ArrayList<>();

    @AfterEach
    void shutdownAll() {
        created.forEach(Observarium::shutdown);
        // Restore the default uncaught exception handler so one test's install()
        // call does not pollute subsequent tests.
        Thread.setDefaultUncaughtExceptionHandler(null);
    }

    // -----------------------------------------------------------------------
    // Stubs
    // -----------------------------------------------------------------------

    /** Records received events and the severity they were submitted with. */
    private static class CapturingPostingService implements PostingService {
        final List<ExceptionEvent> received = new ArrayList<>();
        final List<Severity> severities = new ArrayList<>();

        @Override public String name() { return "capturing"; }

        @Override
        public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
            return DuplicateSearchResult.notFound();
        }

        @Override
        public PostingResult createIssue(ExceptionEvent event) {
            received.add(event);
            severities.add(event.severity());
            return PostingResult.success("ISSUE-1", "https://tracker/ISSUE-1");
        }

        @Override
        public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
            received.add(event);
            severities.add(event.severity());
            return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
        }
    }

    /** Records every uncaughtException invocation. */
    private static class RecordingUncaughtHandler implements Thread.UncaughtExceptionHandler {
        final List<Throwable> received = new ArrayList<>();
        final List<Thread> threads = new ArrayList<>();

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            threads.add(t);
            received.add(e);
        }
    }

    private Observarium buildObservarium(PostingService... services) {
        Observarium.Builder builder = Observarium.builder();
        for (PostingService svc : services) {
            builder.addPostingService(svc);
        }
        Observarium obs = builder.build();
        created.add(obs);
        return obs;
    }

    // -----------------------------------------------------------------------
    // Constructor variants
    // -----------------------------------------------------------------------

    @Test
    void singleArgConstructor_doesNotThrow() {
        Observarium obs = buildObservarium();
        assertDoesNotThrow(() -> new ObservariumExceptionHandler(obs));
    }

    @Test
    void twoArgConstructor_withNullDelegate_doesNotThrow() {
        Observarium obs = buildObservarium();
        assertDoesNotThrow(() -> new ObservariumExceptionHandler(obs, null));
    }

    @Test
    void twoArgConstructor_withDelegate_doesNotThrow() {
        Observarium obs = buildObservarium();
        RecordingUncaughtHandler delegate = new RecordingUncaughtHandler();
        assertDoesNotThrow(() -> new ObservariumExceptionHandler(obs, delegate));
    }

    // -----------------------------------------------------------------------
    // uncaughtException() — captures with FATAL severity
    // -----------------------------------------------------------------------

    @Test
    void uncaughtException_capturesWithFatalSeverity() throws InterruptedException {
        CapturingPostingService svc = new CapturingPostingService();
        Observarium obs = buildObservarium(svc);
        ObservariumExceptionHandler handler = new ObservariumExceptionHandler(obs);

        RuntimeException exception = new RuntimeException("crash");
        handler.uncaughtException(Thread.currentThread(), exception);

        assertFalse(svc.severities.isEmpty(), "Handler must submit the exception for capture");
        assertEquals(Severity.FATAL, svc.severities.get(0),
            "Uncaught exceptions must be captured with FATAL severity");
    }

    @Test
    void uncaughtException_blocksUntilCaptureComplete() {
        // The handler calls .get() on the future, so the service must have been
        // called by the time uncaughtException() returns.
        CapturingPostingService svc = new CapturingPostingService();
        Observarium obs = buildObservarium(svc);
        ObservariumExceptionHandler handler = new ObservariumExceptionHandler(obs);

        handler.uncaughtException(Thread.currentThread(), new RuntimeException("blocking test"));

        assertFalse(svc.received.isEmpty(),
            "The posting service must have been called before uncaughtException() returns");
    }

    // -----------------------------------------------------------------------
    // uncaughtException() — delegate forwarding
    // -----------------------------------------------------------------------

    @Test
    void uncaughtException_withDelegate_forwardsToDelegate() {
        CapturingPostingService svc = new CapturingPostingService();
        Observarium obs = buildObservarium(svc);
        RecordingUncaughtHandler delegate = new RecordingUncaughtHandler();
        ObservariumExceptionHandler handler = new ObservariumExceptionHandler(obs, delegate);

        RuntimeException exception = new RuntimeException("delegated");
        handler.uncaughtException(Thread.currentThread(), exception);

        assertEquals(1, delegate.received.size(), "Delegate must be called once");
        assertSame(exception, delegate.received.get(0),
            "Delegate must receive the original exception");
        assertSame(Thread.currentThread(), delegate.threads.get(0),
            "Delegate must receive the original thread");
    }

    @Test
    void uncaughtException_withNullDelegate_doesNotThrow() {
        Observarium obs = buildObservarium();
        ObservariumExceptionHandler handler = new ObservariumExceptionHandler(obs, null);
        // Must not throw NullPointerException when delegate is null.
        assertDoesNotThrow(() ->
            handler.uncaughtException(Thread.currentThread(), new RuntimeException("no delegate")));
    }

    // -----------------------------------------------------------------------
    // install()
    // -----------------------------------------------------------------------

    @Test
    void install_setsDefaultUncaughtExceptionHandler() {
        Observarium obs = buildObservarium();
        ObservariumExceptionHandler.install(obs);

        Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
        assertNotNull(installed, "install() must set a default uncaught exception handler");
        assertInstanceOf(ObservariumExceptionHandler.class, installed,
            "The installed handler must be an ObservariumExceptionHandler");
    }

    @Test
    void install_wrapsExistingDefaultHandler() {
        // Set up an existing handler first.
        RecordingUncaughtHandler existingHandler = new RecordingUncaughtHandler();
        Thread.setDefaultUncaughtExceptionHandler(existingHandler);

        Observarium obs = buildObservarium();
        ObservariumExceptionHandler.install(obs);

        // The new handler must be an ObservariumExceptionHandler (it wraps the existing one).
        Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
        assertInstanceOf(ObservariumExceptionHandler.class, installed);

        // Trigger it and verify the original handler still runs.
        RuntimeException ex = new RuntimeException("chained");
        installed.uncaughtException(Thread.currentThread(), ex);

        assertEquals(1, existingHandler.received.size(),
            "The previously installed handler must still be invoked after install()");
        assertSame(ex, existingHandler.received.get(0));
    }
}
