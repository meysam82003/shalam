package com.meysam.divanadmin

import android.app.*
import android.app.job.*
import android.content.*
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.math.BigInteger
import java.util.Calendar
import javax.crypto.Cipher
import javax.security.auth.x500.X500Principal

object AdminSessionVault {
    private fun prefs(c:Context)=c.getSharedPreferences("admin",Context.MODE_PRIVATE)
    @Suppress("DEPRECATION") private fun keys(c:Context):KeyStore {
        val k=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        if(!k.containsAlias("divan-admin-token")){
            val end=Calendar.getInstance().apply{add(Calendar.YEAR,20)}
            val spec=KeyPairGeneratorSpec.Builder(c).setAlias("divan-admin-token").setSubject(X500Principal("CN=Divan Admin")).setSerialNumber(BigInteger.ONE).setStartDate(Calendar.getInstance().time).setEndDate(end.time).build()
            KeyPairGenerator.getInstance("RSA","AndroidKeyStore").apply{initialize(spec);generateKeyPair()}
        };return k
    }
    fun save(c:Context,value:String?){if(value==null){prefs(c).edit().remove("token").apply();return};val cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(Cipher.ENCRYPT_MODE,keys(c).getCertificate("divan-admin-token").publicKey);prefs(c).edit().putString("token",Base64.encodeToString(cipher.doFinal(value.toByteArray()),Base64.NO_WRAP)).apply()}
    fun read(c:Context):String?=runCatching{val s=prefs(c).getString("token",null)?:return null;val key=keys(c).getKey("divan-admin-token",null);val cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(Cipher.DECRYPT_MODE,key);String(cipher.doFinal(Base64.decode(s,Base64.NO_WRAP)))}.getOrNull()
}
object AdminNotifications {
    private const val JOB=56010
    private const val CHANNEL="divan_admin_events"
    private fun prefs(c:Context)=c.getSharedPreferences("admin",Context.MODE_PRIVATE)
    fun channels(c:Context){if(Build.VERSION.SDK_INT>=26)(c.getSystemService(Context.NOTIFICATION_SERVICE)as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL,"ثبت‌نام و خرید کاربران",NotificationManager.IMPORTANCE_HIGH).apply{description="رویدادهای واقعی سرور دیوان"})}
    fun schedule(c:Context){channels(c);if(AdminSessionVault.read(c)==null)return;val js=c.getSystemService(Context.JOB_SCHEDULER_SERVICE)as JobScheduler;js.schedule(JobInfo.Builder(JOB,ComponentName(c,AdminEventJob::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build())}
    @Synchronized fun sync(c:Context){
        val token=AdminSessionVault.read(c)?:return;if(!prefs(c).getBoolean("notifications",true))return
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
        val base=prefs(c).getString("url","https://divan-emtiaz.ir/divan/api/index.php")!!
        val initial=!prefs(c).contains("inbox_cursor");var cursor=prefs(c).getLong("inbox_cursor",0)
        val url=URL(base+"?r=admin/mobile/inbox&after=$cursor");require(url.protocol=="https")
        val connection=url.openConnection()as HttpURLConnection;connection.instanceFollowRedirects=false;connection.connectTimeout=12000;connection.readTimeout=20000;connection.setRequestProperty("Authorization","Bearer $token")
        try{
            if(connection.responseCode!=200)return
            val response=JSONObject(connection.inputStream.bufferedReader().use{it.readText()});if(!response.optBoolean("ok"))return;val data=response.getJSONObject("data")
            if(initial){prefs(c).edit().putLong("inbox_cursor",data.optLong("latest_id")).apply();return}
            val events=data.optJSONArray("items")?:return
            for(i in 0 until events.length()){
                val e=events.getJSONObject(i);val id=e.getLong("id");if(id<=cursor)continue
                val intent=Intent(c,AdminActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("inbox",true)
                val pi=PendingIntent.getActivity(c,1,intent,PendingIntent.FLAG_UPDATE_CURRENT or if(Build.VERSION.SDK_INT>=23)PendingIntent.FLAG_IMMUTABLE else 0)
                val b=if(Build.VERSION.SDK_INT>=26)Notification.Builder(c,CHANNEL)else Notification.Builder(c)
                b.setSmallIcon(com.meysam.divanadmin.R.drawable.admin_icon).setContentTitle(e.optString("title")).setContentText(e.optString("body")).setStyle(Notification.BigTextStyle().bigText(e.optString("body"))).setContentIntent(pi).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH)
                (c.getSystemService(Context.NOTIFICATION_SERVICE)as NotificationManager).notify((id%Int.MAX_VALUE).toInt(),b.build())
                cursor=id;prefs(c).edit().putLong("inbox_cursor",cursor).apply()
            }
        }finally{connection.disconnect()}
    }
}
class AdminEventJob:JobService(){override fun onStartJob(params:JobParameters?):Boolean{Thread{try{runCatching{AdminNotifications.sync(applicationContext)}}finally{jobFinished(params,false)}}.start();return true};override fun onStopJob(params:JobParameters?)=true}
