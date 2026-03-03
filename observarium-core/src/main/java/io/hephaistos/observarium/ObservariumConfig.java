package io.hephaistos.observarium;

import io.hephaistos.observarium.scrub.ScrubLevel;

public record ObservariumConfig(
    ScrubLevel scrubLevel,
    int postingServiceCount
) {}
