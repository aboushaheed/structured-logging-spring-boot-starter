package com.izemtechnologies.logging.masking;

/**
 * Defines the different modes for masking sensitive data in logs.
 * 
 * @author Souidi
 * @since 1.1.0
 */
public enum MaskingMode {

    /**
     * Completely replaces the value with a fixed mask string.
     * Example: "john@example.com" → "***MASKED***"
     */
    FULL,

    /**
     * Partially masks the value, preserving some characters for recognition.
     * Example: "john@example.com" → "j***@e***.com"
     * Example: "4111111111111111" → "4111****1111"
     */
    PARTIAL,

    /**
     * Replaces the value with a SHA-256 hash (truncated).
     * Allows correlation without exposing the actual value.
     * Example: "john@example.com" → "hash:a1b2c3d4e5f6"
     */
    HASH,

    /**
     * Replaces with a reversible token (for debugging purposes).
     * Requires a token store to reverse the masking.
     * Example: "john@example.com" → "token:TK123456"
     */
    TOKENIZE,

    /**
     * Redacts the value entirely, replacing with "[REDACTED]".
     * Similar to FULL but with a standard redaction marker.
     */
    REDACT,

    /**
     * Preserves the format but replaces characters.
     * Example: "john@example.com" → "xxxx@xxxxxxx.xxx"
     * Example: "555-123-4567" → "xxx-xxx-xxxx"
     */
    FORMAT_PRESERVING
}
