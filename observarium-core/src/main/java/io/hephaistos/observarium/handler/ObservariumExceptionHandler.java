package io.hephaistos.observarium.handler;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.Severity;

public class ObservariumExceptionHandler implements Thread.UncaughtExceptionHandler {

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
        observarium.captureException(e, Severity.FATAL);
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
