package com.izemtechnologies.logging.routing;

/**
 * Defines the available log destinations.
 * 
 * @author Souidi
 * @since 1.1.0
 */
public enum LogDestination {

    /**
     * Console output (stdout/stderr).
     */
    CONSOLE,

    /**
     * File-based logging.
     */
    FILE,

    /**
     * Elasticsearch/OpenSearch.
     */
    ELASTICSEARCH,

    /**
     * Logstash (TCP/UDP).
     */
    LOGSTASH,

    /**
     * Kafka topic.
     */
    KAFKA,

    /**
     * AWS CloudWatch Logs.
     */
    CLOUDWATCH,

    /**
     * Google Cloud Logging (Stackdriver).
     */
    GCP_LOGGING,

    /**
     * Azure Monitor Logs.
     */
    AZURE_MONITOR,

    /**
     * Datadog.
     */
    DATADOG,

    /**
     * Splunk.
     */
    SPLUNK,

    /**
     * Loki (Grafana).
     */
    LOKI,

    /**
     * Custom HTTP endpoint.
     */
    HTTP,

    /**
     * Syslog (RFC 5424).
     */
    SYSLOG,

    /**
     * Null destination (discard).
     */
    NULL
}
