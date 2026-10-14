package android.text;

public interface Editable extends CharSequence, Appendable {
    Editable replace(int st, int en, CharSequence text);
    Editable insert(int where, CharSequence text);
    Editable delete(int st, int en);
    @Override Editable append(CharSequence text);
    @Override Editable append(CharSequence text, int start, int end);
    @Override Editable append(char text);
    void clear();
    void clearSpans();
    void setFilters(InputFilter[] filters);
    InputFilter[] getFilters();
}
