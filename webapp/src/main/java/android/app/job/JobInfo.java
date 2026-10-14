package android.app.job;

public class JobInfo {
    public static final int NETWORK_TYPE_NONE = 0, NETWORK_TYPE_ANY = 1, NETWORK_TYPE_UNMETERED = 2;
    final int id;
    final long period;
    final android.content.ComponentName service;

    JobInfo(int id, long period, android.content.ComponentName service) { this.id = id; this.period = period; this.service = service; }

    public int getId() { return id; }
    public long getIntervalMillis() { return period; }
    public android.content.ComponentName getService() { return service; }

    public static final class Builder {
        private final int id;
        private final android.content.ComponentName service;
        private long period;
        public Builder(int jobId, android.content.ComponentName jobService) { id = jobId; service = jobService; }
        public Builder setPeriodic(long intervalMillis) { period = intervalMillis; return this; }
        public Builder setPeriodic(long intervalMillis, long flexMillis) { period = intervalMillis; return this; }
        public Builder setPersisted(boolean isPersisted) { return this; }
        public Builder setRequiredNetworkType(int networkType) { return this; }
        public Builder setMinimumLatency(long minLatencyMillis) { return this; }
        public JobInfo build() { return new JobInfo(id, period, service); }
    }
}
