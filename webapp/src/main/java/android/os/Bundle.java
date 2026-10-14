package android.os;

public final class Bundle extends BaseBundle implements Parcelable {
    public Bundle() {}
    public Bundle(Bundle b) { if (b != null) map.putAll(b.map); }
    public void putFloat(String key, float value) { map.put(key, value); }
    public float getFloat(String key) { Object o = map.get(key); return o instanceof Float ? (Float) o : 0f; }
    public void putParcelable(String key, Parcelable value) { map.put(key, value); }
    @SuppressWarnings("unchecked") public <T extends Parcelable> T getParcelable(String key) { return (T) map.get(key); }
    public void putBundle(String key, Bundle value) { map.put(key, value); }
    public Bundle getBundle(String key) { Object o = map.get(key); return o instanceof Bundle ? (Bundle) o : null; }
    public void putAll(Bundle b) { map.putAll(b.map); }
}
