package android.util;

public class SparseArray<E> {
    private final java.util.TreeMap<Integer, E> map = new java.util.TreeMap<>();
    public E get(int key) { return map.get(key); }
    public E get(int key, E valueIfKeyNotFound) { E v = map.get(key); return v != null ? v : valueIfKeyNotFound; }
    public void put(int key, E value) { map.put(key, value); }
    public void remove(int key) { map.remove(key); }
    public int size() { return map.size(); }
    public void clear() { map.clear(); }
}
