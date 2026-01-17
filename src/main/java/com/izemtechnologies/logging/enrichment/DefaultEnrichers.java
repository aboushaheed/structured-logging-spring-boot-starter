package com.izemtechnologies.logging.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Collection of default log enrichers providing system and environment context.
 * 
 * @author Souidi
 * @since 1.1.0
 */
public class DefaultEnrichers {

    /**
     * Enricher that adds application metadata.
     */
    @Slf4j
    @Component
    public static class ApplicationEnricher implements LogEnricher {

        @Value("${spring.application.name:unknown}")
        private String applicationName;

        @Value("${spring.profiles.active:default}")
        private String activeProfiles;

        private final Optional<BuildProperties> buildProperties;
        private final Environment environment;

        private Map<String, Object> cachedData;

        public ApplicationEnricher(Optional<BuildProperties> buildProperties, Environment environment) {
            this.buildProperties = buildProperties;
            this.environment = environment;
        }

        @Override
        public Map<String, Object> enrich() {
            if (cachedData == null) {
                cachedData = new HashMap<>();
                cachedData.put("app.name", applicationName);
                cachedData.put("app.profiles", activeProfiles);

                buildProperties.ifPresent(props -> {
                    cachedData.put("app.version", props.getVersion());
                    cachedData.put("app.artifact", props.getArtifact());
                    cachedData.put("app.group", props.getGroup());
                    if (props.getTime() != null) {
                        cachedData.put("app.build_time", props.getTime().toString());
                    }
                });
            }
            return cachedData;
        }

        @Override
        public int getOrder() {
            return -100; // Run early
        }

        @Override
        public String getName() {
            return "ApplicationEnricher";
        }
    }

    /**
     * Enricher that adds host and infrastructure information.
     */
    @Slf4j
    @Component
    public static class HostEnricher implements LogEnricher {

        private Map<String, Object> cachedData;

        @Override
        public Map<String, Object> enrich() {
            if (cachedData == null) {
                cachedData = new HashMap<>();
                try {
                    InetAddress localhost = InetAddress.getLocalHost();
                    cachedData.put("host.name", localhost.getHostName());
                    cachedData.put("host.ip", localhost.getHostAddress());
                } catch (Exception e) {
                    log.debug("Could not determine host info", e);
                    cachedData.put("host.name", "unknown");
                }

                // Container/Kubernetes detection
                String podName = System.getenv("HOSTNAME");
                if (podName != null) {
                    cachedData.put("k8s.pod", podName);
                }

                String namespace = System.getenv("KUBERNETES_NAMESPACE");
                if (namespace != null) {
                    cachedData.put("k8s.namespace", namespace);
                }

                String nodeName = System.getenv("KUBERNETES_NODE_NAME");
                if (nodeName != null) {
                    cachedData.put("k8s.node", nodeName);
                }

                // Docker detection
                String containerId = System.getenv("CONTAINER_ID");
                if (containerId != null) {
                    cachedData.put("container.id", containerId);
                }
            }
            return cachedData;
        }

        @Override
        public int getOrder() {
            return -90;
        }

        @Override
        public String getName() {
            return "HostEnricher";
        }
    }

    /**
     * Enricher that adds JVM and runtime information.
     */
    @Slf4j
    @Component
    public static class RuntimeEnricher implements LogEnricher {

        private Map<String, Object> cachedData;
        private final RuntimeMXBean runtimeMXBean;

        public RuntimeEnricher() {
            this.runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        }

        @Override
        public Map<String, Object> enrich() {
            if (cachedData == null) {
                cachedData = new HashMap<>();
                cachedData.put("jvm.name", runtimeMXBean.getVmName());
                cachedData.put("jvm.version", runtimeMXBean.getVmVersion());
                cachedData.put("jvm.vendor", runtimeMXBean.getVmVendor());
                cachedData.put("process.pid", runtimeMXBean.getPid());
            }
            return cachedData;
        }

        @Override
        public int getOrder() {
            return -80;
        }

        @Override
        public String getName() {
            return "RuntimeEnricher";
        }
    }

    /**
     * Enricher that adds Git commit information.
     */
    @Slf4j
    @Component
    public static class GitEnricher implements LogEnricher {

        private final Optional<GitProperties> gitProperties;
        private Map<String, Object> cachedData;

        public GitEnricher(Optional<GitProperties> gitProperties) {
            this.gitProperties = gitProperties;
        }

        @Override
        public Map<String, Object> enrich() {
            if (cachedData == null) {
                cachedData = new HashMap<>();
                gitProperties.ifPresent(props -> {
                    cachedData.put("git.commit", props.getShortCommitId());
                    cachedData.put("git.branch", props.getBranch());
                    if (props.getCommitTime() != null) {
                        cachedData.put("git.commit_time", props.getCommitTime().toString());
                    }
                });
            }
            return cachedData;
        }

        @Override
        public int getOrder() {
            return -70;
        }

        @Override
        public boolean isActive() {
            return gitProperties.isPresent();
        }

        @Override
        public String getName() {
            return "GitEnricher";
        }
    }

    /**
     * Enricher that adds environment variables (configurable whitelist).
     */
    @Slf4j
    @Component
    public static class EnvironmentEnricher implements LogEnricher {

        @Value("${app.logging.enrichment.env-vars:ENV,REGION,DATACENTER,CLUSTER}")
        private String envVarWhitelist;

        private Map<String, Object> cachedData;

        @Override
        public Map<String, Object> enrich() {
            if (cachedData == null) {
                cachedData = new HashMap<>();
                String[] vars = envVarWhitelist.split(",");
                for (String var : vars) {
                    String value = System.getenv(var.trim());
                    if (value != null && !value.isBlank()) {
                        cachedData.put("env." + var.trim().toLowerCase(), value);
                    }
                }
            }
            return cachedData;
        }

        @Override
        public int getOrder() {
            return -60;
        }

        @Override
        public String getName() {
            return "EnvironmentEnricher";
        }
    }

    /**
     * Enricher that adds thread context information (dynamic, not cached).
     */
    @Slf4j
    @Component
    public static class ThreadEnricher implements LogEnricher {

        @Override
        public Map<String, Object> enrich() {
            Thread currentThread = Thread.currentThread();
            return Map.of(
                "thread.name", currentThread.getName(),
                "thread.id", currentThread.threadId()
            );
        }

        @Override
        public int getOrder() {
            return 50; // Run later, dynamic data
        }

        @Override
        public String getName() {
            return "ThreadEnricher";
        }
    }

    /**
     * Enricher that adds memory usage information (dynamic, sampled).
     */
    @Slf4j
    @Component
    public static class MemoryEnricher implements LogEnricher {

        private long lastUpdate = 0;
        private Map<String, Object> cachedData = new HashMap<>();
        private static final long UPDATE_INTERVAL_MS = 5000; // Update every 5 seconds

        @Override
        public Map<String, Object> enrich() {
            long now = System.currentTimeMillis();
            if (now - lastUpdate > UPDATE_INTERVAL_MS) {
                Runtime runtime = Runtime.getRuntime();
                long totalMemory = runtime.totalMemory();
                long freeMemory = runtime.freeMemory();
                long usedMemory = totalMemory - freeMemory;

                cachedData = Map.of(
                    "memory.used_mb", usedMemory / (1024 * 1024),
                    "memory.total_mb", totalMemory / (1024 * 1024),
                    "memory.free_mb", freeMemory / (1024 * 1024)
                );
                lastUpdate = now;
            }
            return cachedData;
        }

        @Override
        public int getOrder() {
            return 100; // Run last
        }

        @Override
        public boolean isActive() {
            return false; // Disabled by default, can be enabled via config
        }

        @Override
        public String getName() {
            return "MemoryEnricher";
        }
    }
}
