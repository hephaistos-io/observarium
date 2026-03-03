package io.hephaistos.observarium.handler;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class ObservariumExceptionHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ObservariumExceptionHandler.class);
    private static final long FATAL_WAIT_SECONDS = 5;

    private final Observarium observarium;
    private final Thread.UncaughtExceptionHandler delegate;

    public ObservariumExceptionHandler(Observarium observarium) {
        this(observarium, null);
    }

    public ObservariumExceptionHandler(Observarium observarium,
                                        Thread.UncaughtExceptionHandler delegate) {
        this.observarium = observarium;
        this.delegate = delegate;
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            // Block briefly to ensure the fatal exception report is delivered
            // before the JVM exits.
            observarium.captureException(e, Severity.FATAL)
                    .get(FATAL_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.error("Failed to deliver fatal exception report", ex);
        }
        if (delegate != null) {
            delegate.uncaughtException(t, e);
        }
    }

    public static void install(Observarium observarium) {
        Thread.UncaughtExceptionHandler existing =
            Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(
            new ObservariumExceptionHandler(observarium, existing));
    }
}
