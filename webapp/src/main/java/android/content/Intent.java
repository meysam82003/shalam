package android.content;

public class Intent implements android.os.Parcelable {
    public static final String ACTION_MAIN = "android.intent.action.MAIN", ACTION_VIEW = "android.intent.action.VIEW",
        ACTION_SEND = "android.intent.action.SEND", ACTION_SENDTO = "android.intent.action.SENDTO",
        ACTION_OPEN_DOCUMENT = "android.intent.action.OPEN_DOCUMENT", ACTION_CREATE_DOCUMENT = "android.intent.action.CREATE_DOCUMENT",
        ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT", ACTION_CHOOSER = "android.intent.action.CHOOSER",
        ACTION_DIAL = "android.intent.action.DIAL", ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED",
        ACTION_PICK = "android.intent.action.PICK";
    public static final String CATEGORY_OPENABLE = "android.intent.category.OPENABLE", CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER",
        CATEGORY_DEFAULT = "android.intent.category.DEFAULT", CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE";
    public static final String EXTRA_TITLE = "android.intent.extra.TITLE", EXTRA_TEXT = "android.intent.extra.TEXT",
        EXTRA_SUBJECT = "android.intent.extra.SUBJECT", EXTRA_STREAM = "android.intent.extra.STREAM", EXTRA_EMAIL = "android.intent.extra.EMAIL",
        EXTRA_MIME_TYPES = "android.intent.extra.MIME_TYPES", EXTRA_INTENT = "android.intent.extra.INTENT", EXTRA_ALLOW_MULTIPLE = "android.intent.extra.ALLOW_MULTIPLE";
    public static final int FLAG_GRANT_READ_URI_PERMISSION = 1, FLAG_GRANT_WRITE_URI_PERMISSION = 2, FLAG_ACTIVITY_NEW_TASK = 0x10000000,
        FLAG_ACTIVITY_CLEAR_TOP = 0x04000000, FLAG_ACTIVITY_SINGLE_TOP = 0x20000000, FLAG_ACTIVITY_CLEAR_TASK = 0x00008000;

    private String action;
    private android.net.Uri data;
    private String type;
    private int flags;
    private final java.util.LinkedHashSet<String> categories = new java.util.LinkedHashSet<>();
    private final android.os.Bundle extras = new android.os.Bundle();
    private ClipData clip;
    private ComponentName component;
    private String pkg;

    public static final int URI_INTENT_SCHEME = 1;
    private Intent selector;

    /** «intent:» links of a web page: only the plain address part is kept (the browser opens it). */
    public static Intent parseUri(String uri, int flags) throws java.net.URISyntaxException {
        if (uri == null) throw new java.net.URISyntaxException("null", "no uri");
        String u = uri;
        if (u.startsWith("intent:")) {
            int hash = u.indexOf("#Intent;");
            String body = hash >= 0 ? u.substring(7, hash) : u.substring(7);
            String scheme = "https";
            if (hash >= 0) for (String part : u.substring(hash + 8).split(";")) if (part.startsWith("scheme=")) scheme = part.substring(7);
            u = scheme + ":" + body;
        }
        return new Intent(ACTION_VIEW, android.net.Uri.parse(u));
    }

    public Intent getSelector() { return selector; }
    public void setSelector(Intent s) { selector = s; }

    public Intent() {}
    public Intent(String action) { this.action = action; }
    public Intent(String action, android.net.Uri uri) { this.action = action; this.data = uri; }
    public Intent(Context context, Class<?> cls) { component = new ComponentName(context, cls); }
    public Intent(Intent o) { action = o.action; data = o.data; type = o.type; flags = o.flags; categories.addAll(o.categories); extras.putAll(o.extras); clip = o.clip; component = o.component; }

    public String getAction() { return action; }
    public Intent setAction(String action) { this.action = action; return this; }
    public android.net.Uri getData() { return data; }
    public Intent setData(android.net.Uri data) { this.data = data; return this; }
    public String getType() { return type; }
    public Intent setType(String type) { this.type = type; return this; }
    public Intent setDataAndType(android.net.Uri data, String type) { this.data = data; this.type = type; return this; }
    public int getFlags() { return flags; }
    public Intent setFlags(int flags) { this.flags = flags; return this; }
    public Intent addFlags(int flags) { this.flags |= flags; return this; }
    public Intent addCategory(String category) { categories.add(category); return this; }
    public java.util.Set<String> getCategories() { return categories; }
    public Intent setPackage(String packageName) { pkg = packageName; return this; }
    public String getPackage() { return pkg; }
    public ComponentName getComponent() { return component; }
    public Intent setComponent(ComponentName c) { component = c; return this; }
    public Intent setClassName(Context ctx, String cls) { component = new ComponentName(ctx.getPackageName(), cls); return this; }
    public ClipData getClipData() { return clip; }
    public void setClipData(ClipData clip) { this.clip = clip; }
    public android.os.Bundle getExtras() { return extras; }
    public boolean hasExtra(String name) { return extras.containsKey(name); }
    public void removeExtra(String name) { extras.remove(name); }
    public Intent putExtra(String name, String value) { extras.putString(name, value); return this; }
    public Intent putExtra(String name, int value) { extras.putInt(name, value); return this; }
    public Intent putExtra(String name, long value) { extras.putLong(name, value); return this; }
    public Intent putExtra(String name, boolean value) { extras.putBoolean(name, value); return this; }
    public Intent putExtra(String name, String[] value) { extras.putStringArray(name, value); return this; }
    public Intent putExtra(String name, android.os.Parcelable value) { extras.putParcelable(name, value); return this; }
    public Intent putExtra(String name, CharSequence value) { extras.putString(name, value == null ? null : value.toString()); return this; }
    public Intent putExtras(android.os.Bundle b) { extras.putAll(b); return this; }
    public String getStringExtra(String name) { Object o = extras.get(name); return o == null ? null : o.toString(); }
    public int getIntExtra(String name, int defaultValue) { return extras.getInt(name, defaultValue); }
    public long getLongExtra(String name, long defaultValue) { return extras.getLong(name, defaultValue); }
    public boolean getBooleanExtra(String name, boolean defaultValue) { return extras.getBoolean(name, defaultValue); }
    public String[] getStringArrayExtra(String name) { return extras.getStringArray(name); }
    @SuppressWarnings("unchecked") public <T extends android.os.Parcelable> T getParcelableExtra(String name) { return (T) extras.get(name); }
    public CharSequence getCharSequenceExtra(String name) { return getStringExtra(name); }

    public static Intent createChooser(Intent target, CharSequence title) {
        Intent i = new Intent(ACTION_CHOOSER);
        i.putExtra(EXTRA_INTENT, target);
        i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    public Intent resolveActivity(Object pm) { return this; }
}
