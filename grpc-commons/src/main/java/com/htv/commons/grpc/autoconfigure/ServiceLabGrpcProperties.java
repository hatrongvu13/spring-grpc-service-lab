package com.htv.commons.grpc.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "service-lab.grpc")
public class ServiceLabGrpcProperties {

    private String serviceName;

    private long defaultDeadlineMs = 2000;

    private long minimumRemainingMs = 100;

    private final Ping ping = new Ping();

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public long getDefaultDeadlineMs() {
        return defaultDeadlineMs;
    }

    public void setDefaultDeadlineMs(long defaultDeadlineMs) {
        this.defaultDeadlineMs = defaultDeadlineMs;
    }

    public long getMinimumRemainingMs() {
        return minimumRemainingMs;
    }

    public void setMinimumRemainingMs(long minimumRemainingMs) {
        this.minimumRemainingMs = minimumRemainingMs;
    }

    public Ping getPing() {
        return ping;
    }

    public static class Ping {

        private String nextHop;

        private long perHopTimeoutMs = 1000;

        public String getNextHop() {
            return nextHop;
        }

        public void setNextHop(String nextHop) {
            this.nextHop = nextHop;
        }

        public long getPerHopTimeoutMs() {
            return perHopTimeoutMs;
        }

        public void setPerHopTimeoutMs(long perHopTimeoutMs) {
            this.perHopTimeoutMs = perHopTimeoutMs;
        }
    }
}