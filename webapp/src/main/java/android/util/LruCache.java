package android.util;

/** AOSP LruCache behaviour (access order, sizeOf, entryRemoved, trimToSize). */
public class LruCache<K, V> {
    private final java.util.LinkedHashMap<K, V> map;
    private int size;
    private int maxSize;
    private int putCount, createCount, evictionCount, hitCount, missCount;

    public LruCache(int maxSize) {
        if (maxSize <= 0) throw new IllegalArgumentException("maxSize <= 0");
        this.maxSize = maxSize;
        this.map = new java.util.LinkedHashMap<>(0, 0.75f, true);
    }

    public void resize(int maxSize) { this.maxSize = maxSize; trimToSize(maxSize); }

    public final V get(K key) {
        if (key == null) throw new NullPointerException("key == null");
        V mapValue = map.get(key);
        if (mapValue != null) { hitCount++; return mapValue; }
        missCount++;
        V createdValue = create(key);
        if (createdValue == null) return null;
        createCount++;
        mapValue = map.put(key, createdValue);
        if (mapValue != null) map.put(key, mapValue); else size += safeSizeOf(key, createdValue);
        if (mapValue != null) { entryRemoved(false, key, createdValue, mapValue); return mapValue; }
        trimToSize(maxSize);
        return createdValue;
    }

    public final V put(K key, V value) {
        if (key == null || value == null) throw new NullPointerException("key == null || value == null");
        putCount++;
        size += safeSizeOf(key, value);
        V previous = map.put(key, value);
        if (previous != null) size -= safeSizeOf(key, previous);
        if (previous != null) entryRemoved(false, key, previous, value);
        trimToSize(maxSize);
        return previous;
    }

    public void trimToSize(int maxSize) {
        while (size > maxSize && !map.isEmpty()) {
            java.util.Map.Entry<K, V> toEvict = map.entrySet().iterator().next();
            K key = toEvict.getKey();
            V value = toEvict.getValue();
            map.remove(key);
            size -= safeSizeOf(key, value);
            evictionCount++;
            entryRemoved(true, key, value, null);
        }
    }

    public final V remove(K key) {
        V previous = map.remove(key);
        if (previous != null) { size -= safeSizeOf(key, previous); entryRemoved(false, key, previous, null); }
        return previous;
    }

    protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {}
    protected V create(K key) { return null; }

    private int safeSizeOf(K key, V value) {
        int result = sizeOf(key, value);
        if (result < 0) throw new IllegalStateException("Negative size: " + key + "=" + value);
        return result;
    }

    protected int sizeOf(K key, V value) { return 1; }
    public final void evictAll() { trimToSize(-1); }
    public final int size() { return size; }
    public final int maxSize() { return maxSize; }
    public final java.util.Map<K, V> snapshot() { return new java.util.LinkedHashMap<>(map); }
}
