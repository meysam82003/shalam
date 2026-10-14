package android.content;

public final class ContentValues {
    private final java.util.HashMap<String, Object> values = new java.util.HashMap<>();
    public void put(String key, String value) { values.put(key, value); }
    public void put(String key, Long value) { values.put(key, value); }
    public Object get(String key) { return values.get(key); }
}
