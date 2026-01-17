package com.izemtechnologies.logging.async;

/**
 * Defines strategies for handling backpressure when the async log buffer is full.
 * 
 * @author Souidi
 * @since 1.1.0
 */
public enum BackpressureStrategy {

    /**
     * Block the calling thread until space is available.
     * This preserves all logs but may impact application performance.
     */
    BLOCK,

    /**
     * Drop the oldest log events to make room for new ones.
     * Preserves recent logs at the cost of older ones.
     */
    DROP_OLDEST,

    /**
     * Drop new log events when the buffer is full.
     * Preserves the historical sequence but may lose recent events.
     */
    DROP_NEWEST,

    /**
     * Sample logs when under pressure - keep every Nth log.
     * Provides a representative sample during high-load periods.
     */
    SAMPLE,

    /**
     * Drop only DEBUG and TRACE logs when under pressure.
     * Preserves important logs (ERROR, WARN, INFO) while shedding verbose ones.
     */
    DROP_LOW_PRIORITY,

    /**
     * Flush the buffer to a secondary destination (e.g., file) when full.
     * Prevents data loss but requires additional configuration.
     */
    OVERFLOW_TO_FILE
}
