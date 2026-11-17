package com.meysam.divanemtiaz

import android.content.Context
import com.meysam.divanpush.PushHooks
import com.meysam.divanpush.PushRuntime
import org.json.JSONObject

object PlayerPushHooks:PushHooks {
    override fun register(context:Context,token:String){
        ProState.init(context)
        val body=JSONObject().put("token",token).put("device",ProState.deviceId).put("store",StoreInfo.storeId).put("offers",Notices.offers(context)&&Notices.canPost(context)).put("pro_optin",Notices.offersPro(context)&&Notices.canPost(context))
        PushRuntime.request(StoreInfo.apiBase,"POST","push/register",body,ProState.token)
    }
    override fun receive(context:Context){Notices.sync(context)}
}
