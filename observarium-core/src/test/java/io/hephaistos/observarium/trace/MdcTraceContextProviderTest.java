package io.hephaistos.observarium.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;

class MdcTraceContextProviderTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // -----------------------------------------------------------------------
    // Default constructor — uses "trace_id" and "span_id" keys
    // -----------------------------------------------------------------------

    @Test
    void defaultConstructor_getTraceId_returnsMdcValue() {
        MDC.put("trace_id", "trace-abc");
        MdcTraceContextProvider provider = new MdcTraceContextProvider();
        assertEquals("trace-abc", provider.getTraceId());
    }

    @Test
    void defaultConstructor_getSpanId_returnsMdcValue() {
        MDC.put("span_id", "span-xyz");
        MdcTraceContextProvider provider = new MdcTraceContextProvider();
        assertEquals("span-xyz", provider.getSpanId());
    }

    @Test
    void defaultConstructor_getTraceId_returnsNull_whenMdcKeyAbsent() {
        // Nothing put into MDC — MDC.get() returns null for missing keys.
        MdcTraceContextProvider provider = new MdcTraceContextProvider();
        assertNull(provider.getTraceId());
    }

    @Test
    void defaultConstructor_getSpanId_returnsNull_whenMdcKeyAbsent() {
        MdcTraceContextProvider provider = new MdcTraceContextProvider();
        assertNull(provider.getSpanId());
    }

    // -----------------------------------------------------------------------
    // Custom key constructor
    // -----------------------------------------------------------------------

    @Test
    void customKeys_constructor_readsFromConfiguredTraceIdKey() {
        MDC.put("X-Trace-Id", "custom-trace-1");
        MdcTraceContextProvider provider = new MdcTraceContextProvider("X-Trace-Id", "X-Span-Id");
        assertEquals("custom-trace-1", provider.getTraceId());
    }

    @Test
    void customKeys_constructor_readsFromConfiguredSpanIdKey() {
        MDC.put("X-Span-Id", "custom-span-2");
        MdcTraceContextProvider provider = new MdcTraceContextProvider("X-Trace-Id", "X-Span-Id");
        assertEquals("custom-span-2", provider.getSpanId());
    }

    @Test
    void customKeys_doNotReadFromDefaultKeys() {
        // The provider is configured with custom keys; values under the default
        // keys must not bleed through.
        MDC.put("trace_id", "default-trace");
        MDC.put("span_id", "default-span");
        MdcTraceContextProvider provider = new MdcTraceContextProvider("X-Trace-Id", "X-Span-Id");
        assertNull(provider.getTraceId(), "Must read from the custom key, not the default one");
        assertNull(provider.getSpanId(), "Must read from the custom key, not the default one");
    }

    @Test
    void getTraceId_reflectsCurrentMdcState_notValueAtConstructionTime() {
        // MDC is read at call time, not at construction time.
        MdcTraceContextProvider provider = new MdcTraceContextProvider();
        assertNull(provider.getTraceId(), "Should be null before MDC is populated");

        MDC.put("trace_id", "late-trace");
        assertEquals("late-trace", provider.getTraceId(),
            "Should return the value now present in the MDC");
    }
}
