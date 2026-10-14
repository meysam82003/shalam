# Divan Emtiaz release rules for R8 (shrinking and obfuscation).

# Readable crash stack traces from the mapping file, without the original file names in the APK.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Created by name (one class per store flavor; the ad network only when compiled in).
-keep class com.meysam.divanemtiaz.FlavorBilling { <init>(); }
-keep class com.meysam.divanemtiaz.TapsellBridge { <init>(...); }

# Enum constants are stored by name in saved games, history and settings: their names never change.
-keepclassmembernames enum com.meysam.divanemtiaz.** { <fields>; }
-keepclassmembers enum com.meysam.divanemtiaz.** { public static **[] values(); public static ** valueOf(java.lang.String); }

# Store billing libraries: their classes and exception names are read by the app and the stores' services.
-keep class ir.cafebazaar.poolakey.** { *; }
-keep class com.farsitel.bazaar.** { *; }
-keep class ir.myket.billingclient.** { *; }
-keep class com.android.vending.billing.** { *; }
-dontwarn ir.cafebazaar.poolakey.**
-dontwarn ir.myket.billingclient.**
-dontwarn ir.tapsell.**
