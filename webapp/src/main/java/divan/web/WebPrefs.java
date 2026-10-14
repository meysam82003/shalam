package divan.web;

import android.content.SharedPreferences;
import org.teavm.jso.JSBody;

/**
 * SharedPreferences kept per name, typed like Android (a getInt on a long fails the same way), saved as JSON in
 * IndexedDB after apply()/commit().
 */
public final class WebPrefs implements SharedPreferences {
    private static final java.util.HashMap<String, WebPrefs> ALL = new java.util.HashMap<>();

    @JSBody(params = "n", script = "var v = DH.boot.prefs[n]; return v == null ? null : v;")
    private static native String bootPrefs(String name);

    @JSBody(params = {"n", "json"}, script = "DH.savePrefs(n, json);")
    private static native void persist(String name, String json);

    private final String name;
    private final java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
    private final java.util.ArrayList<OnSharedPreferenceChangeListener> listeners = new java.util.ArrayList<>();

    public static synchronized WebPrefs get(String name) {
        WebPrefs p = ALL.get(name);
        if (p == null) {
            p = new WebPrefs(name);
            ALL.put(name, p);
        }
        return p;
    }

    private WebPrefs(String name) {
        this.name = name;
        String json = bootPrefs(name);
        if (json != null) {
            try {
                org.json.JSONObject o = new org.json.JSONObject(json);
                java.util.Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    org.json.JSONArray tv = o.getJSONArray(k);
                    String t = tv.getString(0);
                    switch (t) {
                        case "s": map.put(k, tv.getString(1)); break;
                        case "i": map.put(k, tv.getInt(1)); break;
                        case "l": map.put(k, Long.parseLong(tv.getString(1))); break;
                        case "f": map.put(k, (float) tv.getDouble(1)); break;
                        case "b": map.put(k, tv.getBoolean(1)); break;
                        case "set": {
                            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
                            org.json.JSONArray a = tv.getJSONArray(1);
                            for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                            map.put(k, set);
                            break;
                        }
                        default:
                    }
                }
            } catch (org.json.JSONException e) {
                JS.error("prefs " + name + " unreadable: " + e);
            }
        }
    }

    private String toJson() {
        org.json.JSONObject o = new org.json.JSONObject();
        try {
            for (java.util.Map.Entry<String, Object> e : map.entrySet()) {
                Object v = e.getValue();
                org.json.JSONArray tv = new org.json.JSONArray();
                if (v instanceof String) tv.put("s").put(v);
                else if (v instanceof Integer) tv.put("i").put(v);
                else if (v instanceof Long) tv.put("l").put(String.valueOf(v));
                else if (v instanceof Float) tv.put("f").put(((Float) v).doubleValue());
                else if (v instanceof Boolean) tv.put("b").put(v);
                else if (v instanceof java.util.Set) { org.json.JSONArray a = new org.json.JSONArray(); for (Object s : (java.util.Set<?>) v) a.put(String.valueOf(s)); tv.put("set").put(a); }
                else continue;
                o.put(e.getKey(), tv);
            }
        } catch (org.json.JSONException ex) { /* only valid values are stored */ }
        return o.toString();
    }

    @Override public java.util.Map<String, ?> getAll() { return new java.util.HashMap<>(map); }
    @Override public String getString(String key, String defValue) { Object v = map.get(key); return v != null ? (String) v : defValue; }

    @SuppressWarnings("unchecked")
    @Override public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) { Object v = map.get(key); return v != null ? (java.util.Set<String>) v : defValues; }

    @Override public int getInt(String key, int defValue) { Object v = map.get(key); return v != null ? (Integer) v : defValue; }
    @Override public long getLong(String key, long defValue) { Object v = map.get(key); return v != null ? (Long) v : defValue; }
    @Override public float getFloat(String key, float defValue) { Object v = map.get(key); return v != null ? (Float) v : defValue; }
    @Override public boolean getBoolean(String key, boolean defValue) { Object v = map.get(key); return v != null ? (Boolean) v : defValue; }
    @Override public boolean contains(String key) { return map.containsKey(key); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { listeners.add(l); }
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { listeners.remove(l); }

    @Override public Editor edit() { return new Ed(); }

    private final class Ed implements Editor {
        private final java.util.LinkedHashMap<String, Object> changes = new java.util.LinkedHashMap<>();
        private boolean clear;
        private final Object REMOVE = new Object();

        @Override public Editor putString(String key, String value) { changes.put(key, value == null ? REMOVE : value); return this; }
        @Override public Editor putStringSet(String key, java.util.Set<String> values) { changes.put(key, values == null ? REMOVE : new java.util.LinkedHashSet<>(values)); return this; }
        @Override public Editor putInt(String key, int value) { changes.put(key, value); return this; }
        @Override public Editor putLong(String key, long value) { changes.put(key, value); return this; }
        @Override public Editor putFloat(String key, float value) { changes.put(key, value); return this; }
        @Override public Editor putBoolean(String key, boolean value) { changes.put(key, value); return this; }
        @Override public Editor remove(String key) { changes.put(key, REMOVE); return this; }
        @Override public Editor clear() { clear = true; return this; }

        @Override public boolean commit() {
            java.util.ArrayList<String> changed = new java.util.ArrayList<>();
            if (clear) { changed.addAll(map.keySet()); map.clear(); }
            for (java.util.Map.Entry<String, Object> e : changes.entrySet()) {
                if (e.getValue() == REMOVE) map.remove(e.getKey()); else map.put(e.getKey(), e.getValue());
                changed.add(e.getKey());
            }
            persist(name, toJson());
            for (String k : changed) for (OnSharedPreferenceChangeListener l : new java.util.ArrayList<>(listeners)) l.onSharedPreferenceChanged(WebPrefs.this, k);
            return true;
        }

        @Override public void apply() { commit(); }
    }
}
