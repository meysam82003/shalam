package android.net;

/** Uri parsing as needed by the app: scheme, host, path, last segment and query parameters. */
public abstract class Uri implements android.os.Parcelable, Comparable<Uri> {
    public static final Uri EMPTY = parse("");

    public abstract String getScheme();
    public abstract String getHost();
    public abstract String getPath();
    public abstract String getQuery();
    public abstract String getFragment();
    public abstract String getLastPathSegment();
    public abstract java.util.List<String> getPathSegments();
    public abstract String getAuthority();
    public abstract int getPort();
    public abstract String getSchemeSpecificPart();
    public abstract String getEncodedPath();

    public static Uri parse(String uriString) { return new StringUri(uriString); }

    public static Uri fromFile(java.io.File file) { return new StringUri("file://" + file.getAbsolutePath()); }

    public static String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
    }

    public static String decode(String s) {
        if (s == null) return null;
        try { return java.net.URLDecoder.decode(s.replace("+", "%2B"), "UTF-8"); } catch (Exception e) { return s; }
    }

    public String getQueryParameter(String key) {
        String q = getQuery();
        if (q == null) return null;
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            if (decode(k).equals(key)) return eq < 0 ? "" : decode(part.substring(eq + 1));
        }
        return null;
    }

    public java.util.Set<String> getQueryParameterNames() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        String q = getQuery();
        if (q != null) for (String part : q.split("&")) { int eq = part.indexOf('='); names.add(decode(eq < 0 ? part : part.substring(0, eq))); }
        return names;
    }

    public boolean isHierarchical() { return true; }
    public boolean isAbsolute() { return getScheme() != null; }
    public Builder buildUpon() { return new Builder(toString()); }

    @Override public int compareTo(Uri other) { return toString().compareTo(other.toString()); }
    @Override public boolean equals(Object o) { return o instanceof Uri && toString().equals(o.toString()); }
    @Override public int hashCode() { return toString().hashCode(); }

    public static final class Builder {
        private final StringBuilder sb;
        private boolean hasQuery;
        public Builder() { sb = new StringBuilder(); }
        Builder(String base) { sb = new StringBuilder(base); hasQuery = base.contains("?"); }
        public Builder scheme(String s) { sb.insert(0, s + ":"); return this; }
        public Builder authority(String a) { sb.append("//").append(a); return this; }
        public Builder path(String p) { sb.append(p); return this; }
        public Builder appendPath(String p) { if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '/') sb.append('/'); sb.append(encode(p)); return this; }
        public Builder appendQueryParameter(String k, String v) { sb.append(hasQuery ? '&' : '?').append(encode(k)).append('=').append(encode(v)); hasQuery = true; return this; }
        public Uri build() { return parse(sb.toString()); }
    }

    private static final class StringUri extends Uri {
        private final String s;
        private String scheme, ssp, authority, path, query, fragment;

        StringUri(String s) {
            this.s = s;
            String rest = s;
            int hash = rest.indexOf('#');
            if (hash >= 0) { fragment = rest.substring(hash + 1); rest = rest.substring(0, hash); }
            int colon = rest.indexOf(':');
            int slash = rest.indexOf('/');
            if (colon > 0 && (slash < 0 || colon < slash)) { scheme = rest.substring(0, colon); rest = rest.substring(colon + 1); }
            ssp = rest;
            int q = rest.indexOf('?');
            if (q >= 0) { query = rest.substring(q + 1); rest = rest.substring(0, q); }
            if (rest.startsWith("//")) {
                int p = rest.indexOf('/', 2);
                authority = p < 0 ? rest.substring(2) : rest.substring(2, p);
                rest = p < 0 ? "" : rest.substring(p);
            }
            path = rest;
        }

        @Override public String getScheme() { return scheme; }
        @Override public String getAuthority() { return authority; }
        @Override public String getHost() {
            if (authority == null) return null;
            String a = authority;
            int at = a.lastIndexOf('@');
            if (at >= 0) a = a.substring(at + 1);
            int c = a.lastIndexOf(':');
            if (c >= 0 && a.indexOf(']') < c) a = a.substring(0, c);
            return a;
        }
        @Override public int getPort() {
            if (authority == null) return -1;
            int c = authority.lastIndexOf(':');
            try { return c >= 0 ? Integer.parseInt(authority.substring(c + 1)) : -1; } catch (NumberFormatException e) { return -1; }
        }
        @Override public String getPath() { return path == null ? null : decode(path); }
        @Override public String getEncodedPath() { return path; }
        @Override public String getQuery() { return query; }
        @Override public String getFragment() { return fragment; }
        @Override public String getSchemeSpecificPart() { return ssp; }
        @Override public java.util.List<String> getPathSegments() {
            java.util.ArrayList<String> out = new java.util.ArrayList<>();
            if (path != null) for (String p : path.split("/")) if (!p.isEmpty()) out.add(decode(p));
            return out;
        }
        @Override public String getLastPathSegment() {
            java.util.List<String> segs = getPathSegments();
            return segs.isEmpty() ? null : segs.get(segs.size() - 1);
        }
        @Override public String toString() { return s; }
    }
}
