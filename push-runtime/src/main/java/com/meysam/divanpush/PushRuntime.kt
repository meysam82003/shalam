package com.meysam.divanpush

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** No credentials, account tokens or message contents are stored here. Only public Firebase app options. */
interface PushHooks {
    fun register(context: Context, token: String)
    fun receive(context: Context)
}
object PushRuntime {
    private var application:Context?=null
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var checked = 0L
    private fun prefs(c:Context) = c.getSharedPreferences("divan_push_public",Context.MODE_PRIVATE)
    private fun hooks(c:Context):PushHooks? = runCatching {
        @Suppress("DEPRECATION")
        val name=c.packageManager.getApplicationInfo(c.packageName,PackageManager.GET_META_DATA).metaData.getString("divan.pushBridge")!!
        Class.forName(name).getField("INSTANCE").get(null) as PushHooks
    }.getOrNull()
    @Synchronized fun restore(c:Context):Boolean = runCatching {
        application=c.applicationContext
        val cfg=JSONObject(prefs(c).getString("config","{}")!!)
        if(!cfg.optBoolean("enabled"))return false
        val options=FirebaseOptions.Builder().setApplicationId(cfg.getString("app_id")).setApiKey(cfg.getString("api_key")).setProjectId(cfg.getString("project_id")).setGcmSenderId(cfg.getString("sender_id")).build()
        val old=FirebaseApp.getApps(c).firstOrNull{it.name==FirebaseApp.DEFAULT_APP_NAME}
        // Firebase default options cannot be changed in a running process. Apply a changed project after restart.
        if(old!=null && old.options.applicationId!=options.applicationId)return false
        if(old==null)FirebaseApp.initializeApp(c,options)
        FirebaseMessaging.getInstance().isAutoInitEnabled=true
        true
    }.getOrDefault(false)
    fun connect(c:Context,base:String,client:String){
        val ctx=c.applicationContext
        if(System.currentTimeMillis()-checked<60_000)return
        checked=System.currentTimeMillis()
        io.execute {
            runCatching {
                val cfg=request(base,"GET","push/config&client=$client",null,null)?.optJSONObject("data")
                if(cfg!=null)prefs(ctx).edit().putString("config",cfg.toString()).apply()
                if (restore(ctx)) {
                    val messaging = FirebaseMessaging.getInstance()
                    if (client in setOf("bazaar", "myket", "direct")) messaging.subscribeToTopic("divan_android_updates_$client")
                    messaging.token.addOnSuccessListener { token -> io.execute { runCatching { hooks(ctx)?.register(ctx, token) } } }
                }
            }
        }
    }
    fun updateRegistration(c:Context){
        val ctx=c.applicationContext
        if(restore(ctx))FirebaseMessaging.getInstance().token.addOnSuccessListener{token->io.execute{runCatching{hooks(ctx)?.register(ctx,token)}}}
    }
    fun token(c:Context,token:String){io.execute{runCatching{hooks(c)?.register(c,token)}}}
    fun receive(c:Context){if(prefs(c).getString("config",null)?.let{runCatching{JSONObject(it).optBoolean("enabled")}.getOrDefault(false)}==true)hooks(c)?.receive(c)}
    fun request(base:String,method:String,route:String,body:JSONObject?,bearer:String?):JSONObject? {
        val u=URL(base+"?r="+route)
        require(u.protocol=="https" && u.userInfo==null)
        val conn=u.openConnection() as HttpURLConnection
        application?.let{NativeTls.apply(it,conn)}
        conn.instanceFollowRedirects=false;conn.connectTimeout=5000;conn.readTimeout=5000;conn.requestMethod=method
        if(!bearer.isNullOrBlank())conn.setRequestProperty("Authorization","Bearer $bearer")
        try {
            if(body!=null){conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            return if(conn.responseCode==200)JSONObject(conn.inputStream.bufferedReader().use{it.readText()}) else null
        } finally {conn.disconnect()}
    }
}
class DivanPushApplication:Application(){override fun onCreate(){super.onCreate();PushRuntime.restore(this)}}
class DivanMessagingService:FirebaseMessagingService(){
    override fun onNewToken(token:String){PushRuntime.token(applicationContext,token)}
    override fun onMessageReceived(message:RemoteMessage){if(message.data["kind"] in setOf("notices","inbox","app_update"))runCatching{PushRuntime.receive(applicationContext)}}
    override fun onDeletedMessages(){runCatching{PushRuntime.receive(applicationContext)}}
}
