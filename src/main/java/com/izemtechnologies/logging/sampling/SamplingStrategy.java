package com.izemtechnologies.logging.sampling;

/**
 * Defines the different strategies for log sampling.
 * 
 * @author Souidi
 * @since 1.1.0
 */
public enum SamplingStrategy {

    /**
     * No sampling - all logs are recorded.
     */
    NONE,

    /**
     * Fixed rate sampling - a percentage of logs are recorded.
     * Example: rate=0.1 means 10% of logs are kept.
     */
    RATE,

    /**
     * Adaptive sampling - rate adjusts based on system load.
     * Reduces sampling when under high load to prevent log flooding.
     */
    ADAPTIVE,

    /**
     * Head-based sampling - decision made at the start of a request.
     * All logs for a sampled request are kept; all logs for unsampled requests are dropped.
     */
    HEAD_BASED,

    /**
     * Tail-based sampling - decision made at the end of a request.
     * If the request resulted in an error, all logs are kept.
     * Useful for debugging but requires buffering.
     */
    TAIL_BASED,

    /**
     * Priority-based sampling - higher priority logs are always kept.
     * ERROR and WARN are always kept; INFO/DEBUG are sampled.
     */
    PRIORITY,

    /**
     * Count-based sampling - keeps every Nth log.
     * Example: count=10 keeps every 10th log.
     */
    COUNT_BASED
}
