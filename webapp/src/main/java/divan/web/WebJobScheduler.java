package divan.web;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;

/** Periodic jobs while the page is open (the browser runs no code for a closed page). */
public final class WebJobScheduler extends JobScheduler {
    public static final WebJobScheduler INSTANCE = new WebJobScheduler();
    private static final java.util.HashMap<String, java.util.function.Supplier<JobService>> FACTORIES = new java.util.HashMap<>();

    private final java.util.HashMap<Integer, JobInfo> jobs = new java.util.HashMap<>();
    private final java.util.HashMap<Integer, Runnable> timers = new java.util.HashMap<>();
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    private WebJobScheduler() {}

    /** Job services are created by name (no reflection in the browser). */
    public static void register(Class<?> service, java.util.function.Supplier<JobService> factory) { FACTORIES.put(service.getName(), factory); }

    @Override public int schedule(JobInfo job) {
        cancel(job.getId());
        jobs.put(job.getId(), job);
        long every = Math.max(15 * 60_000L, job.getIntervalMillis());
        Runnable r = new Runnable() {
            @Override public void run() {
                java.util.function.Supplier<JobService> f = FACTORIES.get(job.getService().getClassName());
                if (f != null) try { f.get().onStartJob(new JobParameters(job.getId())); } catch (RuntimeException e) { e.printStackTrace(); }
                handler.postDelayed(this, every);
            }
        };
        timers.put(job.getId(), r);
        handler.postDelayed(r, Math.min(every, 60_000L));
        return RESULT_SUCCESS;
    }

    @Override public void cancel(int jobId) {
        Runnable r = timers.remove(jobId);
        if (r != null) handler.removeCallbacks(r);
        jobs.remove(jobId);
    }

    @Override public void cancelAll() { for (Integer id : new java.util.ArrayList<>(jobs.keySet())) cancel(id); }

    @Override public java.util.List<JobInfo> getAllPendingJobs() { return new java.util.ArrayList<>(jobs.values()); }
}
