package com.izemtechnologies.logging.async;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced async appender with intelligent backpressure handling.
 * 
 * <p>This appender extends Logback's AsyncAppender with additional features:</p>
 * <ul>
 *   <li>Multiple backpressure strategies</li>
 *   <li>Metrics for monitoring buffer utilization</li>
 *   <li>Overflow handling to secondary destinations</li>
 *   <li>Priority-based dropping during high load</li>
 * </ul>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Slf4j
@Getter
@Setter
public class BackpressureAsyncAppender extends AsyncAppender {

    private BackpressureStrategy backpressureStrategy = BackpressureStrategy.DROP_OLDEST;
    
    private int bufferSize = 8192;
    private int warningThreshold = 80; // Percentage
    private int criticalThreshold = 95; // Percentage
    
    private String overflowFilePath = "logs/overflow.log";
    private int sampleRateUnderPressure = 10; // Keep every Nth log when sampling
    
    private final AtomicLong totalEvents = new AtomicLong(0);
    private final AtomicLong droppedEvents = new AtomicLong(0);
    private final AtomicLong overflowEvents = new AtomicLong(0);
    private final AtomicLong sampleCounter = new AtomicLong(0);
    
    private volatile boolean warningLogged = false;
    private volatile boolean criticalLogged = false;
    
    private BufferedWriter overflowWriter;
    private final Object overflowLock = new Object();

    @Override
    public void start() {
        // Configure the queue size
        setQueueSize(bufferSize);
        
        // Don't discard by default - we handle it ourselves
        setDiscardingThreshold(0);
        
        // Include caller data based on configuration
        setIncludeCallerData(false);
        
        super.start();
        
        // Initialize overflow file if needed
        if (backpressureStrategy == BackpressureStrategy.OVERFLOW_TO_FILE) {
            initializeOverflowFile();
        }
    }

    @Override
    public void stop() {
        closeOverflowWriter();
        super.stop();
        
        // Log final statistics
        if (droppedEvents.get() > 0 || overflowEvents.get() > 0) {
            addWarn(String.format("Async appender stopped. Total: %d, Dropped: %d, Overflow: %d",
                totalEvents.get(), droppedEvents.get(), overflowEvents.get()));
        }
    }

    @Override
    protected void append(ILoggingEvent event) {
        totalEvents.incrementAndGet();
        
        // Check buffer utilization
        int currentSize = getNumberOfElementsInQueue();
        int utilization = (currentSize * 100) / bufferSize;
        
        // Log warnings at thresholds
        logThresholdWarnings(utilization);
        
        // If buffer is not full, proceed normally
        if (utilization < 100) {
            super.append(event);
            return;
        }
        
        // Buffer is full - apply backpressure strategy
        handleBackpressure(event, utilization);
    }

    private void handleBackpressure(ILoggingEvent event, int utilization) {
        switch (backpressureStrategy) {
            case BLOCK -> handleBlock(event);
            case DROP_OLDEST -> handleDropOldest(event);
            case DROP_NEWEST -> handleDropNewest(event);
            case SAMPLE -> handleSample(event);
            case DROP_LOW_PRIORITY -> handleDropLowPriority(event);
            case OVERFLOW_TO_FILE -> handleOverflowToFile(event);
        }
    }

    private void handleBlock(ILoggingEvent event) {
        // Default AsyncAppender behavior - block until space available
        super.append(event);
    }

    private void handleDropOldest(ILoggingEvent event) {
        // Try to remove oldest and add new
        BlockingQueue<ILoggingEvent> queue = getBlockingQueue();
        if (queue instanceof ArrayBlockingQueue) {
            // Remove oldest
            ILoggingEvent dropped = queue.poll();
            if (dropped != null) {
                droppedEvents.incrementAndGet();
            }
            // Add new event
            super.append(event);
        } else {
            // Fallback: just try to add
            boolean added = queue.offer(event);
            if (!added) {
                droppedEvents.incrementAndGet();
            }
        }
    }

    private void handleDropNewest(ILoggingEvent event) {
        // Simply drop the new event
        droppedEvents.incrementAndGet();
        
        // Periodically log dropped count
        long dropped = droppedEvents.get();
        if (dropped % 1000 == 0) {
            addWarn("Dropped " + dropped + " log events due to buffer overflow");
        }
    }

    private void handleSample(ILoggingEvent event) {
        long count = sampleCounter.incrementAndGet();
        if (count % sampleRateUnderPressure == 0) {
            // Keep this event
            BlockingQueue<ILoggingEvent> queue = getBlockingQueue();
            boolean added = queue.offer(event);
            if (!added) {
                droppedEvents.incrementAndGet();
            }
        } else {
            droppedEvents.incrementAndGet();
        }
    }

    private void handleDropLowPriority(ILoggingEvent event) {
        Level level = event.getLevel();
        
        // Always keep ERROR and WARN
        if (level.toInt() >= Level.WARN_INT) {
            // Try harder to add important logs
            BlockingQueue<ILoggingEvent> queue = getBlockingQueue();
            try {
                boolean added = queue.offer(event, 100, TimeUnit.MILLISECONDS);
                if (!added) {
                    // Last resort: write to overflow
                    writeToOverflow(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                droppedEvents.incrementAndGet();
            }
        } else if (level.toInt() >= Level.INFO_INT) {
            // Try to add INFO, but don't wait
            BlockingQueue<ILoggingEvent> queue = getBlockingQueue();
            boolean added = queue.offer(event);
            if (!added) {
                droppedEvents.incrementAndGet();
            }
        } else {
            // Drop DEBUG and TRACE
            droppedEvents.incrementAndGet();
        }
    }

    private void handleOverflowToFile(ILoggingEvent event) {
        // Try to add to main queue first
        BlockingQueue<ILoggingEvent> queue = getBlockingQueue();
        boolean added = queue.offer(event);
        
        if (!added) {
            // Write to overflow file
            writeToOverflow(event);
        }
    }

    private void writeToOverflow(ILoggingEvent event) {
        synchronized (overflowLock) {
            if (overflowWriter == null) {
                initializeOverflowFile();
            }
            
            if (overflowWriter != null) {
                try {
                    String line = formatEventForOverflow(event);
                    overflowWriter.write(line);
                    overflowWriter.newLine();
                    overflowWriter.flush();
                    overflowEvents.incrementAndGet();
                } catch (IOException e) {
                    droppedEvents.incrementAndGet();
                    addError("Failed to write to overflow file", e);
                }
            } else {
                droppedEvents.incrementAndGet();
            }
        }
    }

    private String formatEventForOverflow(ILoggingEvent event) {
        return String.format("%s [%s] %s %s - %s",
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(event.getTimeStamp()),
                    java.time.ZoneId.systemDefault()
                )
            ),
            event.getLevel(),
            event.getThreadName(),
            event.getLoggerName(),
            event.getFormattedMessage()
        );
    }

    private void initializeOverflowFile() {
        try {
            Path path = Path.of(overflowFilePath);
            Files.createDirectories(path.getParent());
            overflowWriter = Files.newBufferedWriter(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            addError("Failed to initialize overflow file: " + overflowFilePath, e);
        }
    }

    private void closeOverflowWriter() {
        synchronized (overflowLock) {
            if (overflowWriter != null) {
                try {
                    overflowWriter.close();
                } catch (IOException e) {
                    addError("Failed to close overflow writer", e);
                }
                overflowWriter = null;
            }
        }
    }

    private void logThresholdWarnings(int utilization) {
        if (utilization >= criticalThreshold && !criticalLogged) {
            addWarn("Log buffer critical: " + utilization + "% full. Strategy: " + backpressureStrategy);
            criticalLogged = true;
        } else if (utilization >= warningThreshold && !warningLogged) {
            addWarn("Log buffer warning: " + utilization + "% full");
            warningLogged = true;
        } else if (utilization < warningThreshold) {
            warningLogged = false;
            criticalLogged = false;
        }
    }

    @SuppressWarnings("unchecked")
    private BlockingQueue<ILoggingEvent> getBlockingQueue() {
        try {
            java.lang.reflect.Field field = AsyncAppender.class.getDeclaredField("blockingQueue");
            field.setAccessible(true);
            return (BlockingQueue<ILoggingEvent>) field.get(this);
        } catch (Exception e) {
            // Fallback: create a new queue (not ideal but prevents NPE)
            return new ArrayBlockingQueue<>(bufferSize);
        }
    }

    // ==================== Metrics ====================

    /**
     * Returns the current buffer utilization percentage.
     */
    public int getBufferUtilization() {
        return (getNumberOfElementsInQueue() * 100) / bufferSize;
    }

    /**
     * Returns the total number of events processed.
     */
    public long getTotalEvents() {
        return totalEvents.get();
    }

    /**
     * Returns the number of dropped events.
     */
    public long getDroppedEvents() {
        return droppedEvents.get();
    }

    /**
     * Returns the number of events written to overflow.
     */
    public long getOverflowEvents() {
        return overflowEvents.get();
    }

    /**
     * Returns the drop rate as a percentage.
     */
    public double getDropRate() {
        long total = totalEvents.get();
        if (total == 0) return 0.0;
        return (droppedEvents.get() * 100.0) / total;
    }
}
