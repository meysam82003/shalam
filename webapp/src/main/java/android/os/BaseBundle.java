package android.os;

public class BaseBundle {
    final java.util.HashMap<String, Object> map = new java.util.HashMap<>();

    public int size() { return map.size(); }
    public boolean isEmpty() { return map.isEmpty(); }
    public boolean containsKey(String key) { return map.containsKey(key); }
    public Object get(String key) { return map.get(key); }
    public void remove(String key) { map.remove(key); }
    public java.util.Set<String> keySet() { return map.keySet(); }
    public void putString(String key, String value) { map.put(key, value); }
    public String getString(String key) { Object o = map.get(key); return o instanceof String ? (String) o : null; }
    public String getString(String key, String defaultValue) { String s = getString(key); return s == null ? defaultValue : s; }
    public void putLong(String key, long value) { map.put(key, value); }
    public long getLong(String key) { return getLong(key, 0L); }
    public long getLong(String key, long defaultValue) { Object o = map.get(key); return o instanceof Long ? (Long) o : defaultValue; }
    public void putInt(String key, int value) { map.put(key, value); }
    public int getInt(String key) { return getInt(key, 0); }
    public int getInt(String key, int defaultValue) { Object o = map.get(key); return o instanceof Integer ? (Integer) o : defaultValue; }
    public void putBoolean(String key, boolean value) { map.put(key, value); }
    public boolean getBoolean(String key) { return getBoolean(key, false); }
    public boolean getBoolean(String key, boolean defaultValue) { Object o = map.get(key); return o instanceof Boolean ? (Boolean) o : defaultValue; }
    public void putStringArray(String key, String[] value) { map.put(key, value); }
    public String[] getStringArray(String key) { Object o = map.get(key); return o instanceof String[] ? (String[]) o : null; }
}
