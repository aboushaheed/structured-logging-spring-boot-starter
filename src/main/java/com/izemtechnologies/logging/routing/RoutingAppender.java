package com.izemtechnologies.logging.routing;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

/**
 * Logback appender that routes log events to different appenders based on rules.
 * 
 * <p>This appender uses the {@link LogRouter} to determine which destinations
 * should receive each log event, then forwards the event to the appropriate
 * child appenders.</p>
 * 
 * @author Souidi
 * @since 1.1.0
 */
@Getter
@Setter
public class RoutingAppender extends AppenderBase<ILoggingEvent> implements AppenderAttachable<ILoggingEvent> {

    private static LogRouter router;

    private final AppenderAttachableImpl<ILoggingEvent> appenderAttachable = new AppenderAttachableImpl<>();
    private final Map<LogDestination, Appender<ILoggingEvent>> destinationAppenders = new EnumMap<>(LogDestination.class);

    /**
     * Sets the router instance (called during auto-configuration).
     */
    public static void setRouter(LogRouter routerInstance) {
        router = routerInstance;
    }

    /**
     * Registers an appender for a specific destination.
     */
    public void registerDestination(LogDestination destination, Appender<ILoggingEvent> appender) {
        destinationAppenders.put(destination, appender);
        addAppender(appender);
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (router == null) {
            // No router configured, forward to all appenders
            appenderAttachable.appendLoopOnAppenders(event);
            return;
        }

        // Get destinations for this event
        Set<LogDestination> destinations = router.route(event);

        // Forward to matching appenders
        for (LogDestination destination : destinations) {
            Appender<ILoggingEvent> appender = destinationAppenders.get(destination);
            if (appender != null && appender.isStarted()) {
                appender.doAppend(event);
            }
        }
    }

    @Override
    public void start() {
        // Start all child appenders
        Iterator<Appender<ILoggingEvent>> it = appenderAttachable.iteratorForAppenders();
        while (it.hasNext()) {
            Appender<ILoggingEvent> appender = it.next();
            if (!appender.isStarted()) {
                appender.start();
            }
        }
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        // Stop all child appenders
        Iterator<Appender<ILoggingEvent>> it = appenderAttachable.iteratorForAppenders();
        while (it.hasNext()) {
            Appender<ILoggingEvent> appender = it.next();
            if (appender.isStarted()) {
                appender.stop();
            }
        }
    }

    // ==================== AppenderAttachable Implementation ====================

    @Override
    public void addAppender(Appender<ILoggingEvent> newAppender) {
        appenderAttachable.addAppender(newAppender);
    }

    @Override
    public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
        return appenderAttachable.iteratorForAppenders();
    }

    @Override
    public Appender<ILoggingEvent> getAppender(String name) {
        return appenderAttachable.getAppender(name);
    }

    @Override
    public boolean isAttached(Appender<ILoggingEvent> appender) {
        return appenderAttachable.isAttached(appender);
    }

    @Override
    public void detachAndStopAllAppenders() {
        appenderAttachable.detachAndStopAllAppenders();
    }

    @Override
    public boolean detachAppender(Appender<ILoggingEvent> appender) {
        return appenderAttachable.detachAppender(appender);
    }

    @Override
    public boolean detachAppender(String name) {
        return appenderAttachable.detachAppender(name);
    }
}
