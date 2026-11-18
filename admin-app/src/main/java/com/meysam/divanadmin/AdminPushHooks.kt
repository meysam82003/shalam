package com.meysam.divanadmin

import android.content.Context
import com.meysam.divanpush.PushHooks
import com.meysam.divanpush.PushRuntime
import org.json.JSONObject

object AdminPushHooks:PushHooks {
    override fun register(context:Context,token:String){
        val prefs=context.getSharedPreferences("admin",Context.MODE_PRIVATE)
        val bearer=AdminSessionVault.read(context)?:return
        val response=PushRuntime.request(prefs.getString("url","https://divan-emtiaz.ir/divan/api/index.php")!!,"POST","admin/mobile/push/register",JSONObject().put("token",token).put("enabled",prefs.getBoolean("notifications",true)),bearer)
        if(!prefs.contains("inbox_cursor"))response?.optJSONObject("data")?.let{prefs.edit().putLong("inbox_cursor",it.optLong("baseline")).apply()}
    }
    override fun receive(context:Context){AdminNotifications.sync(context)}
}
