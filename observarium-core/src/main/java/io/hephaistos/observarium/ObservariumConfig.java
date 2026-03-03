package io.hephaistos.observarium;

import io.hephaistos.observarium.scrub.ScrubLevel;

public record ObservariumConfig(
    String apiKey,
    ScrubLevel scrubLevel,
    int postingServiceCount
) {}
