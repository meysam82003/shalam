package android.content;

public final class ComponentName {
    private final String pkg, cls;
    public ComponentName(Context context, Class<?> cls) { this.pkg = context.getPackageName(); this.cls = cls.getName(); }
    public ComponentName(String pkg, String cls) { this.pkg = pkg; this.cls = cls; }
    public String getPackageName() { return pkg; }
    public String getClassName() { return cls; }
}
