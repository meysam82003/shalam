package android.content.res;

public class ColorStateList {
    private final int[][] states;
    private final int[] colors;

    public ColorStateList(int[][] states, int[] colors) { this.states = states; this.colors = colors; }

    public static ColorStateList valueOf(int color) { return new ColorStateList(new int[][] {new int[0]}, new int[] {color}); }

    public int getDefaultColor() { return colors.length > 0 ? colors[colors.length - 1] : 0; }

    public boolean isStateful() { return states.length > 1 || (states.length == 1 && states[0].length > 0); }

    public int getColorForState(int[] stateSet, int defaultColor) {
        for (int i = 0; i < states.length; i++) {
            boolean ok = true;
            for (int s : states[i]) {
                boolean want = s > 0, has = false;
                for (int t : stateSet) if (t == Math.abs(s)) { has = true; break; }
                if (want != has) { ok = false; break; }
            }
            if (ok) return colors[i];
        }
        return defaultColor;
    }
}
