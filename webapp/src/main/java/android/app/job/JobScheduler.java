package android.app.job;

/** Periodic background checks run while the page is open (browsers do not run pages in the background). */
public abstract class JobScheduler {
    public static final int RESULT_FAILURE = 0, RESULT_SUCCESS = 1;
    public abstract int schedule(JobInfo job);
    public abstract void cancel(int jobId);
    public abstract void cancelAll();
    public abstract java.util.List<JobInfo> getAllPendingJobs();
}
