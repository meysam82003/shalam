package divan.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSObject;

/**
 * Calls into the browser. Every function here is a thin call to the page's host script (web/host.js, global «DH»)
 * or to a canvas object; the logic stays in Java so it matches the Android classes it implements.
 */
public final class JS {
    private JS() {}

    // ------------------------------------------------------------------ values
    @JSBody(params = "s", script = "return s;")
    public static native JSObject str(String s);

    @JSBody(params = "o", script = "return o == null;")
    public static native boolean isNull(JSObject o);

    @JSBody(script = "return Date.now();")
    public static native double now();

    @JSBody(script = "return performance.now();")
    public static native double perfNow();

    @JSBody(params = "m", script = "console.log(m);")
    public static native void log(String m);

    @JSBody(params = "m", script = "console.error(m);")
    public static native void error(String m);

    // ------------------------------------------------------------------ canvas objects
    @JSBody(params = {"w", "h"}, script = "return DH.newCanvas(w, h);")
    public static native JSObject newCanvas(int w, int h);

    @JSBody(params = "c", script = "return c.getContext('2d');")
    public static native JSObject context2d(JSObject canvas);

    @JSBody(params = {"c", "w", "h"}, script = "c.width = w; c.height = h;")
    public static native void resizeCanvas(JSObject canvas, int w, int h);

    @JSBody(params = "c", script = "c.width = 0; c.height = 0;")
    public static native void releaseCanvas(JSObject canvas);

    // ------------------------------------------------------------------ paths
    @JSBody(params = "cmds", script = "return DH.path2d(cmds);")
    public static native JSObject path2d(@JSByRef float[] cmds);

    @JSBody(params = {"p", "a", "b", "c", "d", "e", "f"}, script = "var q = new Path2D(); q.addPath(p, new DOMMatrix([a, b, c, d, e, f])); return q;")
    public static native JSObject transformPath(JSObject p, float a, float b, float c, float d, float e, float f);

    @JSBody(params = {"a", "b", "op"}, script = "return DH.pathOp(a, b, op);")
    @JSByRef
    public static native float[] pathOp(@JSByRef float[] a, @JSByRef float[] b, int op);

    // ------------------------------------------------------------------ styles
    @JSBody(params = {"x0", "y0", "x1", "y1", "colors", "pos"}, script = "return DH.linear(x0, y0, x1, y1, colors, pos);")
    public static native JSObject linearGradient(float x0, float y0, float x1, float y1, @JSByRef int[] colors, @JSByRef float[] pos);

    @JSBody(params = {"cx", "cy", "r", "colors", "pos"}, script = "return DH.radial(cx, cy, r, colors, pos);")
    public static native JSObject radialGradient(float cx, float cy, float r, @JSByRef int[] colors, @JSByRef float[] pos);

    @JSBody(params = {"cx", "cy", "colors", "pos"}, script = "return DH.sweep(cx, cy, colors, pos);")
    public static native JSObject sweepGradient(float cx, float cy, @JSByRef int[] colors, @JSByRef float[] pos);

    @JSBody(params = {"img", "rx", "ry"}, script = "return DH.pattern(img, rx, ry);")
    public static native JSObject pattern(JSObject image, boolean repeatX, boolean repeatY);

    @JSBody(params = {"p", "a", "b", "c", "d", "e", "f"}, script = "if (p.setTransform) p.setTransform(new DOMMatrix([a, b, c, d, e, f]));")
    public static native void patternTransform(JSObject pattern, float a, float b, float c, float d, float e, float f);

    // ------------------------------------------------------------------ drawing state
    @JSBody(params = {"x", "font"}, script = "x.font = font;")
    public static native void font(JSObject ctx, String font);

    @JSBody(params = {"x", "s", "a"}, script = "x.fillStyle = s; x.globalAlpha = a;")
    public static native void fillStyle(JSObject ctx, JSObject style, float alpha);

    @JSBody(params = {"x", "s", "a", "w", "cap", "join", "miter", "dash", "phase"},
        script = "x.strokeStyle = s; x.globalAlpha = a; x.lineWidth = w; x.lineCap = cap; x.lineJoin = join; x.miterLimit = miter; x.setLineDash(dash || DH.noDash); x.lineDashOffset = phase;")
    public static native void strokeStyle(JSObject ctx, JSObject style, float alpha, float width, String cap, String join, float miter, @JSByRef float[] dash, float phase);

    @JSBody(params = "x", script = "x.save();")
    public static native void save(JSObject ctx);

    @JSBody(params = "x", script = "x.restore();")
    public static native void restore(JSObject ctx);

    @JSBody(params = {"x", "dx", "dy"}, script = "x.translate(dx, dy);")
    public static native void translate(JSObject ctx, float dx, float dy);

    @JSBody(params = {"x", "sx", "sy"}, script = "x.scale(sx, sy);")
    public static native void scale(JSObject ctx, float sx, float sy);

    @JSBody(params = {"x", "rad"}, script = "x.rotate(rad);")
    public static native void rotate(JSObject ctx, float rad);

    @JSBody(params = {"x", "a", "b", "c", "d", "e", "f"}, script = "x.transform(a, b, c, d, e, f);")
    public static native void transform(JSObject ctx, float a, float b, float c, float d, float e, float f);

    @JSBody(params = {"x", "a", "b", "c", "d", "e", "f"}, script = "x.setTransform(a, b, c, d, e, f);")
    public static native void setTransform(JSObject ctx, float a, float b, float c, float d, float e, float f);

    @JSBody(params = "x", script = "var m = x.getTransform(); return [m.a, m.b, m.c, m.d, m.e, m.f];")
    @JSByRef
    public static native float[] getTransform(JSObject ctx);

    @JSBody(params = {"x", "l", "t", "r", "b"}, script = "x.beginPath(); x.rect(l, t, r - l, b - t); x.clip();")
    public static native void clipRect(JSObject ctx, float l, float t, float r, float b);

    @JSBody(params = {"x", "p", "eo"}, script = "x.clip(p, eo ? 'evenodd' : 'nonzero');")
    public static native void clipPath(JSObject ctx, JSObject path, boolean evenOdd);

    @JSBody(params = {"x", "a"}, script = "x.globalAlpha = a;")
    public static native void globalAlpha(JSObject ctx, float a);

    @JSBody(params = {"x", "op"}, script = "x.globalCompositeOperation = op;")
    public static native void composite(JSObject ctx, String op);

    // ------------------------------------------------------------------ drawing
    @JSBody(params = {"x", "l", "t", "r", "b"}, script = "x.fillRect(l, t, r - l, b - t);")
    public static native void fillRect(JSObject ctx, float l, float t, float r, float b);

    @JSBody(params = {"x", "l", "t", "r", "b"}, script = "x.strokeRect(l, t, r - l, b - t);")
    public static native void strokeRect(JSObject ctx, float l, float t, float r, float b);

    @JSBody(params = {"x", "l", "t", "r", "b"}, script = "x.clearRect(l, t, r - l, b - t);")
    public static native void clearRect(JSObject ctx, float l, float t, float r, float b);

    @JSBody(params = {"x", "l", "t", "r", "b", "rx", "ry", "stroke"}, script = "DH.roundRect(x, l, t, r, b, rx, ry); if (stroke) x.stroke(); else x.fill();")
    public static native void roundRect(JSObject ctx, float l, float t, float r, float b, float rx, float ry, boolean stroke);

    @JSBody(params = {"x", "cx", "cy", "rx", "ry", "stroke"}, script = "x.beginPath(); x.ellipse(cx, cy, Math.max(0, rx), Math.max(0, ry), 0, 0, Math.PI * 2); if (stroke) x.stroke(); else x.fill();")
    public static native void ellipse(JSObject ctx, float cx, float cy, float rx, float ry, boolean stroke);

    @JSBody(params = {"x", "cx", "cy", "rx", "ry", "start", "sweep", "center", "stroke"}, script = "DH.arc(x, cx, cy, rx, ry, start, sweep, center); if (stroke) x.stroke(); else x.fill();")
    public static native void arc(JSObject ctx, float cx, float cy, float rx, float ry, float startDeg, float sweepDeg, boolean useCenter, boolean stroke);

    @JSBody(params = {"x", "x0", "y0", "x1", "y1"}, script = "x.beginPath(); x.moveTo(x0, y0); x.lineTo(x1, y1); x.stroke();")
    public static native void line(JSObject ctx, float x0, float y0, float x1, float y1);

    @JSBody(params = {"x", "p", "eo"}, script = "x.fill(p, eo ? 'evenodd' : 'nonzero');")
    public static native void fillPath(JSObject ctx, JSObject path, boolean evenOdd);

    @JSBody(params = {"x", "p"}, script = "x.stroke(p);")
    public static native void strokePath(JSObject ctx, JSObject path);

    @JSBody(params = {"x", "img", "dx", "dy"}, script = "x.drawImage(img, dx, dy);")
    public static native void drawImage(JSObject ctx, JSObject image, float dx, float dy);

    @JSBody(params = {"x", "img", "sx", "sy", "sw", "sh", "dx", "dy", "dw", "dh"}, script = "if (sw > 0 && sh > 0 && dw > 0 && dh > 0) x.drawImage(img, sx, sy, sw, sh, dx, dy, dw, dh);")
    public static native void drawImageRect(JSObject ctx, JSObject image, float sx, float sy, float sw, float sh, float dx, float dy, float dw, float dh);

    @JSBody(params = {"x", "smooth"}, script = "x.imageSmoothingEnabled = smooth; x.imageSmoothingQuality = 'high';")
    public static native void smoothing(JSObject ctx, boolean smooth);

    /** Draws one run of text; direction is the paragraph direction («ltr»/«rtl»), align is physical (left/center/right). */
    @JSBody(params = {"x", "s", "px", "py", "align", "dir", "stroke", "spacing"},
        script = "x.direction = dir; x.textAlign = align; x.textBaseline = 'alphabetic'; if (spacing) DH.spacedText(x, s, px, py, spacing, stroke); else if (stroke) x.strokeText(s, px, py); else x.fillText(s, px, py);")
    public static native void text(JSObject ctx, String s, float x, float y, String align, String dir, boolean stroke, float spacing);

    @JSBody(params = {"font", "s", "dir"}, script = "return DH.measure(font, s, dir);")
    public static native float measure(String font, String s, String dir);

    // ------------------------------------------------------------------ images
    @JSBody(params = {"c", "mime", "q"}, script = "return DH.encode(c, mime, q);")
    @JSByRef
    public static native byte[] encodeImage(JSObject canvas, String mime, float quality);

    @JSBody(params = "key", script = "return DH.decoded(key);")
    public static native JSObject decodedImage(String key);

    @JSBody(params = {"key", "bytes"}, script = "DH.decodeLater(key, bytes);")
    public static native void decodeLater(String key, @JSByRef byte[] bytes);

    @org.teavm.jso.JSFunctor
    public interface Done extends JSObject { void call(); }

    @JSBody(params = {"key", "bytes", "cb"}, script = "DH.decodeThen(key, bytes, cb);")
    public static native void decodeThen(String key, @JSByRef byte[] bytes, Done cb);

    @JSBody(params = {"key", "c"}, script = "DH.rememberDecoded(key, c);")
    public static native void rememberDecoded(String key, JSObject canvas);

    @JSBody(params = "img", script = "return img.width | 0;")
    public static native int imageWidth(JSObject img);

    @JSBody(params = "img", script = "return img.height | 0;")
    public static native int imageHeight(JSObject img);

    @JSBody(params = {"c", "x", "y"}, script = "var d = c.getContext('2d').getImageData(x, y, 1, 1).data; return ((d[3] << 24) | (d[0] << 16) | (d[1] << 8) | d[2]);")
    public static native int pixel(JSObject canvas, int x, int y);
}
