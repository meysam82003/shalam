package android.graphics;

/** Font family and weight; the Vazirmatn files of the app are registered with the browser as «Vazir» (host.js). */
public class Typeface {
    public static final int NORMAL = 0, BOLD = 1, ITALIC = 2, BOLD_ITALIC = 3;

    public static final Typeface DEFAULT = new Typeface("Vazir", false, false);
    public static final Typeface DEFAULT_BOLD = new Typeface("Vazir", true, false);
    public static final Typeface SANS_SERIF = new Typeface("Vazir", false, false);
    public static final Typeface SERIF = new Typeface("serif", false, false);
    public static final Typeface MONOSPACE = new Typeface("monospace", false, false);

    final String family;
    final boolean bold;
    final boolean italic;
    /** True for the Vazirmatn files, whose metrics are known exactly (see Paint). */
    final boolean vazir;

    private Typeface(String family, boolean bold, boolean italic) {
        this.family = family; this.bold = bold; this.italic = italic;
        this.vazir = "Vazir".equals(family);
    }

    public static Typeface create(String familyName, int style) {
        boolean b = (style & BOLD) != 0, i = (style & ITALIC) != 0;
        if (familyName != null && familyName.startsWith("monospace")) return new Typeface("monospace", b, i);
        if (familyName != null && familyName.startsWith("serif")) return new Typeface("serif", b, i);
        return new Typeface("Vazir", b, i);
    }

    public static Typeface create(Typeface family, int style) {
        String f = family == null ? "Vazir" : family.family;
        return new Typeface(f, (style & BOLD) != 0, (style & ITALIC) != 0);
    }

    public static Typeface createFromAsset(android.content.res.AssetManager mgr, String path) {
        if (path.contains("Vazirmatn")) return new Typeface("Vazir", path.contains("Bold"), false);
        throw new RuntimeException("Font asset not found " + path);
    }

    public static Typeface defaultFromStyle(int style) { return create((String) null, style); }

    public int getStyle() { return (bold ? BOLD : 0) | (italic ? ITALIC : 0); }
    public final boolean isBold() { return bold; }
    public final boolean isItalic() { return italic; }

    /** CSS font shorthand for a size in pixels. */
    String css(float size) {
        String fam = vazir ? "Vazir, Tahoma, sans-serif" : family;
        return (italic ? "italic " : "") + (bold ? "700 " : "400 ") + size + "px " + fam;
    }
}
