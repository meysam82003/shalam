package android.app.job;

public abstract class JobService extends android.content.ContextWrapper {
    public JobService() {}
    public abstract boolean onStartJob(JobParameters params);
    public abstract boolean onStopJob(JobParameters params);
    public final void jobFinished(JobParameters params, boolean wantsReschedule) {}
}
