package android.database;

public class MatrixCursor implements Cursor {
    private final String[] columns;
    private final java.util.ArrayList<Object[]> rows = new java.util.ArrayList<>();
    private int pos = -1;

    public MatrixCursor(String[] columnNames) { columns = columnNames; }
    public MatrixCursor(String[] columnNames, int initialCapacity) { this(columnNames); }
    public void addRow(Object[] columnValues) { rows.add(columnValues); }
    public void addRow(Iterable<?> columnValues) { java.util.ArrayList<Object> l = new java.util.ArrayList<>(); for (Object o : columnValues) l.add(o); rows.add(l.toArray()); }
    @Override public int getCount() { return rows.size(); }
    @Override public boolean moveToFirst() { pos = 0; return !rows.isEmpty(); }
    @Override public boolean moveToNext() { pos++; return pos < rows.size(); }
    @Override public int getColumnIndex(String name) { for (int i = 0; i < columns.length; i++) if (columns[i].equals(name)) return i; return -1; }
    private Object value(int i) { return pos >= 0 && pos < rows.size() && i >= 0 && i < rows.get(pos).length ? rows.get(pos)[i] : null; }
    @Override public String getString(int i) { Object v = value(i); return v == null ? null : v.toString(); }
    @Override public long getLong(int i) { Object v = value(i); return v instanceof Number ? ((Number) v).longValue() : 0; }
    @Override public int getInt(int i) { return (int) getLong(i); }
    @Override public boolean isNull(int i) { return value(i) == null; }
    @Override public void close() {}
}
