package com.meysam.divanemtiaz;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import java.util.Collections;
import java.util.List;

/** The browser uses file transfer. The Android camera activity is only built into the APK. */
public final class TransferPlatform {
    private TransferPlatform() {}
    public static boolean available() { return false; }
    public static void seal(String text, kotlin.jvm.functions.Function2<String, String, kotlin.Unit> done) { done.invoke(null, null); }
    public static void unseal(String key, String text, kotlin.jvm.functions.Function1<String, kotlin.Unit> done) { done.invoke(null); }
    public static List<String> frames(String text) { return Collections.emptyList(); }
    public static Bitmap bitmap(String text) { return null; }
    public static void startScan(Activity activity) {}
    public static boolean isScan(int request) { return false; }
    public static String result(int request, int code, Intent intent) { return null; }
    public static Uri receivedFile(Intent intent) { return null; }
    public static final class Scan {
        public int getTotal() { return 0; }
        public int getReceived() { return 0; }
        public String add(String text) { return null; }
    }
}
