package com.izemtechnologies.logging.provider;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import com.fasterxml.jackson.core.JsonGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Logback JsonProvider that transforms exceptions into structured JSON format.
 * 
 * <p>Instead of outputting stack traces as multi-line strings, this provider
 * creates a structured JSON representation that is easier to parse, index,
 * and query in log aggregation systems like Elasticsearch.</p>
 * 
 * <h2>Output Format:</h2>
 * <pre>{@code
 * {
 *   "exception": {
 *     "type": "java.lang.NullPointerException",
 *     "message": "Cannot invoke method on null object",
 *     "stack_trace": [
 *       {"class": "com.example.Service", "method": "process", "file": "Service.java", "line": 42},
 *       {"class": "com.example.Controller", "method": "handle", "file": "Controller.java", "line": 15}
 *     ],
 *     "caused_by": {
 *       "type": "java.sql.SQLException",
 *       "message": "Connection refused"
 *     },
 *     "root_cause": "java.net.ConnectException",
 *     "frame_count": 25,
 *     "common_frames_omitted": 15
 *   }
 * }
 * }</pre>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Getter
@Setter
public class StructuredExceptionJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    /**
     * Field name for the exception object.
     */
    private String fieldName = "exception";

    /**
     * Maximum number of stack frames to include.
     */
    private int maxStackFrames = 30;

    /**
     * Maximum depth of cause chain to include.
     */
    private int maxCauseDepth = 5;

    /**
     * Whether to include the full class name or just the simple name.
     */
    private boolean useSimpleClassName = false;

    /**
     * Whether to include suppressed exceptions.
     */
    private boolean includeSuppressed = true;

    /**
     * Package prefixes to highlight (application packages).
     */
    private Set<String> applicationPackages = new HashSet<>();

    /**
     * Whether to include a hash of the stack trace for deduplication.
     */
    private boolean includeStackHash = true;

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable == null) {
            return;
        }

        try {
            generator.writeObjectFieldStart(fieldName);
            writeException(generator, throwable, 0);
            generator.writeEndObject();
        } catch (Exception e) {
            // Defensive: never let exception formatting crash the application
            if (log.isDebugEnabled()) {
                log.debug("Error formatting exception to JSON", e);
            }
        }
    }

    private void writeException(JsonGenerator generator, IThrowableProxy throwable, int depth) throws IOException {
        // Type and message
        String className = useSimpleClassName 
            ? getSimpleClassName(throwable.getClassName())
            : throwable.getClassName();
        
        generator.writeStringField("type", className);
        
        if (throwable.getMessage() != null) {
            generator.writeStringField("message", throwable.getMessage());
        }

        // Stack trace as structured array
        writeStackTrace(generator, throwable);

        // Cause chain
        if (throwable.getCause() != null && depth < maxCauseDepth) {
            generator.writeObjectFieldStart("caused_by");
            writeException(generator, throwable.getCause(), depth + 1);
            generator.writeEndObject();
        }

        // Root cause (for quick access)
        if (depth == 0) {
            IThrowableProxy rootCause = findRootCause(throwable);
            if (rootCause != throwable) {
                generator.writeStringField("root_cause", rootCause.getClassName());
                if (rootCause.getMessage() != null) {
                    generator.writeStringField("root_cause_message", rootCause.getMessage());
                }
            }
        }

        // Suppressed exceptions
        if (includeSuppressed && throwable.getSuppressed() != null && throwable.getSuppressed().length > 0) {
            generator.writeArrayFieldStart("suppressed");
            for (IThrowableProxy suppressed : throwable.getSuppressed()) {
                generator.writeStartObject();
                generator.writeStringField("type", suppressed.getClassName());
                if (suppressed.getMessage() != null) {
                    generator.writeStringField("message", suppressed.getMessage());
                }
                generator.writeEndObject();
            }
            generator.writeEndArray();
        }

        // Stack hash for deduplication
        if (includeStackHash && depth == 0) {
            generator.writeStringField("stack_hash", computeStackHash(throwable));
        }

        // Frame counts
        if (depth == 0) {
            generator.writeNumberField("frame_count", countFrames(throwable));
            if (throwable.getCommonFrames() > 0) {
                generator.writeNumberField("common_frames_omitted", throwable.getCommonFrames());
            }
        }
    }

    private void writeStackTrace(JsonGenerator generator, IThrowableProxy throwable) throws IOException {
        StackTraceElementProxy[] frames = throwable.getStackTraceElementProxyArray();
        if (frames == null || frames.length == 0) {
            return;
        }

        generator.writeArrayFieldStart("stack_trace");

        int frameCount = 0;
        boolean truncated = false;

        for (StackTraceElementProxy frame : frames) {
            if (frameCount >= maxStackFrames) {
                truncated = true;
                break;
            }

            StackTraceElement element = frame.getStackTraceElement();
            generator.writeStartObject();

            // Class name
            String className = useSimpleClassName 
                ? getSimpleClassName(element.getClassName())
                : element.getClassName();
            generator.writeStringField("class", className);

            // Method name
            generator.writeStringField("method", element.getMethodName());

            // File name (if available)
            if (element.getFileName() != null) {
                generator.writeStringField("file", element.getFileName());
            }

            // Line number
            if (element.getLineNumber() >= 0) {
                generator.writeNumberField("line", element.getLineNumber());
            } else if (element.getLineNumber() == -2) {
                generator.writeStringField("line", "native");
            }

            // Mark application frames
            if (isApplicationFrame(element.getClassName())) {
                generator.writeBooleanField("app", true);
            }

            generator.writeEndObject();
            frameCount++;
        }

        generator.writeEndArray();

        if (truncated) {
            generator.writeBooleanField("stack_truncated", true);
            generator.writeNumberField("total_frames", frames.length);
        }
    }

    private IThrowableProxy findRootCause(IThrowableProxy throwable) {
        IThrowableProxy current = throwable;
        int depth = 0;
        while (current.getCause() != null && depth < 100) { // Prevent infinite loops
            current = current.getCause();
            depth++;
        }
        return current;
    }

    private String getSimpleClassName(String fullClassName) {
        if (fullClassName == null) {
            return "Unknown";
        }
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot >= 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }

    private boolean isApplicationFrame(String className) {
        if (applicationPackages.isEmpty()) {
            // Default: consider non-java, non-spring packages as application
            return !className.startsWith("java.") 
                && !className.startsWith("javax.")
                && !className.startsWith("sun.")
                && !className.startsWith("com.sun.")
                && !className.startsWith("org.springframework.")
                && !className.startsWith("org.apache.")
                && !className.startsWith("ch.qos.logback.")
                && !className.startsWith("org.slf4j.");
        }
        
        for (String pkg : applicationPackages) {
            if (className.startsWith(pkg)) {
                return true;
            }
        }
        return false;
    }

    private int countFrames(IThrowableProxy throwable) {
        StackTraceElementProxy[] frames = throwable.getStackTraceElementProxyArray();
        return frames != null ? frames.length : 0;
    }

    private String computeStackHash(IThrowableProxy throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClassName());
        
        StackTraceElementProxy[] frames = throwable.getStackTraceElementProxyArray();
        if (frames != null) {
            // Use first 5 frames for hash
            int count = Math.min(5, frames.length);
            for (int i = 0; i < count; i++) {
                StackTraceElement element = frames[i].getStackTraceElement();
                sb.append("|").append(element.getClassName())
                  .append(".").append(element.getMethodName())
                  .append(":").append(element.getLineNumber());
            }
        }
        
        // Simple hash
        int hash = sb.toString().hashCode();
        return String.format("%08x", hash);
    }
}
