package com.meysam.divanemtiaz

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import org.json.JSONObject
import org.json.JSONArray
import java.lang.ref.WeakReference
import java.util.UUID

/** SDK boundary: production implementations are compiled in Android only, never into the shared browser UI. */
interface IranianAdAdapter {
    fun configure(activity: Activity, appId: String, test: Boolean): Boolean
    fun load(activity: Activity, format: String, zone: String, done: (Boolean) -> Unit)
    fun ready(format: String, zone: String): Boolean
    fun show(activity: Activity, format: String, zone: String, event: (String) -> Unit)
    fun banner(activity: Activity, zone: String, box: FrameLayout, event: (String) -> Unit)
    fun destroyBanner(box: FrameLayout)
    fun release()
}

/** Pure policy: active play never becomes an ad opportunity. Counts are committed only on an impression. */
object NetworkAdPolicy {
    fun canInterstitial(hands: Int, every: Int, now: Long, last: Long, count: Int, cap: Int, safe: Boolean): Boolean =
        safe && hands >= every.coerceAtLeast(1) && count < cap && (last == 0L || now - last >= 180.coerceAtLeast(0))
    fun canBanner(page: String?, sensitive: Boolean, pro: Boolean, vipAds: Boolean, allowed: Set<String>): Boolean =
        !sensitive && page != null && page in allowed && (!pro || vipAds)
}

object AdsManager {
    private var context: Context? = null
    private val adapters = HashMap<String, IranianAdAdapter>()
    private val initialized = HashMap<String, String>()
    private var cached: JSONObject? = null
    private var fetchedAt = 0L
    private var fetching = false
    private var fullScreen = false
    private var rewardBusy = false
    private var bannerEpoch = 0
    private val banners = ArrayList<Pair<IranianAdAdapter, FrameLayout>>()
    private val prefs get() = context?.getSharedPreferences("divan_network_ads", Context.MODE_PRIVATE)
    private fun id() = UUID.randomUUID().toString()
    fun init(c: Context) { if (context == null) context = c.applicationContext }
    private fun config(): JSONObject? {
        val c = cached ?: ProState.config.optJSONObject("iran_ads")?.takeIf { ProState.configAge() <= 300 } ?: return null
        return c.takeIf { it.optBoolean("enabled") && (fetchedAt == 0L || ProState.now() - fetchedAt <= 600) }
    }
    fun active() = config() != null
    fun proRewardAvailable(realOnly:Boolean=false) = chain("rewarded").any{(name,cfg)->
        (!realOnly||!cfg.optBoolean("test")) && runCatching{adapters[name]?.ready("rewarded",cfg.getJSONObject("zones").getString("rewarded"))==true}.getOrDefault(false)
    }
    private var preparingReward=false
    fun prepareReward(host:MainActivity){
        if(preparingReward||fullScreen||rewardBusy||!ProState.loggedIn||ProState.isPro||ProState.rewardNextAt>ProState.now()||proRewardPending())return
        val sources=chain("rewarded");if(sources.isEmpty())return
        preparingReward=true
        fun attempt(index:Int){
            if(index>=sources.size||host.isFinishing||host.current !is HomeScreen){preparingReward=false;return}
            val(name,cfg)=sources[index];val impl=adapter(host,name,cfg)
            if(impl==null){attempt(index+1);return}
            val zone=cfg.getJSONObject("zones").getString("rewarded")
            if(runCatching{impl.ready("rewarded",zone)}.getOrDefault(false)){preparingReward=false;return}
            event(name,"rewarded",zone,"request")
            sdkLoad(impl,host,"rewarded",zone){ok->
                event(name,"rewarded",zone,if(ok)"loaded" else "error")
                if(ok){preparingReward=false;(host.current as? HomeScreen)?.let{RewardInvitation.offer(it)}}else attempt(index+1)
            }
        }
        attempt(0)
    }
    fun proRewardPending():Boolean { val q=pending(ProState.userId);return (0 until q.length()).any {q.getJSONObject(it).optString("purpose")=="pro"} }
    fun refresh(host: MainActivity) {
        init(host)
        if (fetching || fetchedAt > 0 && ProState.now() - fetchedAt < 300) return
        fetching = true
        Api.call("GET", "ads/config", null, ProState.token) { data, _ ->
            fetching = false
            val previous = cached?.optLong("revision")
            if (data != null) { cached = data; fetchedAt = ProState.now(); if (previous != data.optLong("revision") && host.current?.adZone != null) host.refresh() }
            if (host.isFinishing) return@call
            prepare(host)
            recover(host)
        }
    }
    private fun chain(format: String): List<Pair<String, JSONObject>> {
        val c = config() ?: return emptyList()
        if(format!="rewarded" && ProState.now()<ProState.adFreeUntil)return emptyList()
        if (!c.optJSONObject(format).let { it != null && it.optBoolean("enabled") }) return emptyList()
        if (ProState.isPro && !c.optBoolean("vip_ads")) return emptyList()
        return listOf(c.optString("primary"), c.optString("fallback")).distinct().mapNotNull { n ->
            val v = c.optJSONObject("networks")?.optJSONObject(n)
            if (v == null || !v.optBoolean("enabled") || v.optString("app_id").isBlank() || v.optJSONObject("zones")?.optString(format).isNullOrBlank()) null else n to v
        }
    }
    private fun adapter(a: Activity, name: String, cfg: JSONObject): IranianAdAdapter? {
        val impl = adapters[name] ?: runCatching {
            val cls = when (name) { "tapsell" -> "TapsellIranAdapter"; "adivery" -> "AdiveryIranAdapter"; else -> return null }
            (Class.forName("com.meysam.divanemtiaz." + cls).getDeclaredConstructor().newInstance() as IranianAdAdapter).also { adapters[name] = it }
        }.getOrNull() ?: return null
        val key = cfg.optString("app_id")
        // Both SDKs initialize once per process. A changed app ID is applied on a subsequent app start.
        if (initialized[name] != null && initialized[name] != key) return null
        if (initialized[name] == null) {
            if (!runCatching { impl.configure(a, key, cfg.optBoolean("test")) }.getOrDefault(false)) return null
            initialized[name] = key
        }
        return impl
    }
    private fun sdkLoad(impl:IranianAdAdapter,host:Activity,format:String,zone:String,done:(Boolean)->Unit){
        var settled=false
        fun finish(ok:Boolean){if(!settled){settled=true;done(ok)}}
        runCatching{impl.load(host,format,zone,::finish)}.onFailure{finish(false)}
    }
    private fun sdkShow(impl:IranianAdAdapter,host:Activity,format:String,zone:String,event:(String)->Unit){
        var ended=false
        fun deliver(kind:String){if(!ended){if(kind=="close"||kind=="error")ended=true;event(kind)}}
        runCatching{impl.show(host,format,zone,::deliver)}.onFailure{deliver("error")}
    }
    private fun event(name: String, format: String, zone: String, kind: String, watch: String? = null, eventId: String = id(), done: ((Boolean) -> Unit)? = null) {
        if (!ProState.loggedIn) { done?.invoke(false); return }
        val e = JSONObject().put("id", eventId).put("network", name).put("format", format).put("placement", zone).put("event", kind)
        if (watch != null) e.put("watch_id", watch)
        Api.call("POST", "ads/events", JSONObject().put("events", JSONArray().put(e)), ProState.token) { data, _ -> done?.invoke(data != null) }
    }
    fun releaseBanners() { bannerEpoch++; AdEngine.invalidateNetworkSticky(); banners.forEach { runCatching { it.first.destroyBanner(it.second) } }; banners.clear() }
    fun release() { releaseBanners(); adapters.values.forEach { runCatching { it.release() } }; adapters.clear(); initialized.clear(); fullScreen=false; rewardBusy=false; preparingReward=false }
    fun banner(screen: Screen, placement: String, fallback: (() -> View?)? = null): View? {
        init(screen.host)
        val c = config() ?: return null
        val rules = c.optJSONObject("banner") ?: return null
        val pages = rules.optJSONArray("pages")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty()
        if (!rules.optBoolean("enabled") || !NetworkAdPolicy.canBanner(screen.adZone, screen.sessionId != null || screen.mafiaArea && screen.adZone == null, ProState.isPro, c.optBoolean("vip_ads"), pages)) return null
        if (!rules.optJSONObject("placements").let { it != null && it.optBoolean(placement, false) }) return null
        val width=screen.host.resources.displayMetrics.widthPixels/screen.host.resources.displayMetrics.density-(if(placement.startsWith("sticky_"))0 else 32)
        if(width<320)return null // A standard 320dp SDK creative must fit without covering controls.
        val epoch=bannerEpoch
        val sources = chain("banner"); if (sources.isEmpty()) return null
        val host = WeakReference(screen.host)
        val wrap = screen.kit.vertical().apply { layoutParams = screen.kit.spaced(10); visibility = View.GONE }
        wrap.addView(screen.kit.text("تبلیغات", TextStyle.CAPTION, Royal.muted))
        val box = FrameLayout(screen.host);wrap.addView(box)
        fun attempt(index: Int) {
            val activity = host.get() ?: return
            if (epoch!=bannerEpoch || activity.isFinishing || activity.current !== screen) { wrap.visibility = View.GONE;return }
            if(index >= sources.size){val direct=fallback?.invoke();wrap.removeAllViews();if(direct!=null){wrap.addView(direct);wrap.visibility=View.VISIBLE}else wrap.visibility=View.GONE;return}
            val (name,n) = sources[index]; val zone = n.getJSONObject("zones").getString("banner")
            val impl = adapter(activity,name,n)
            if (impl == null) { attempt(index+1);return }
            banners += impl to box
            event(name,"banner",zone,"request")
            var failed = false
            var visible = false
            fun callback(kind:String) {
                if (!failed && epoch==bannerEpoch && activity.current === screen && !activity.isFinishing) {
                    event(name,"banner",zone,kind)
                    // Loading must reveal the SDK view before it can observe visibility and report an impression.
                    if (kind == "loaded" || kind == "impression") { visible=true;wrap.visibility = View.VISIBLE }
                    if (kind == "error") { failed = true;runCatching{impl.destroyBanner(box)};box.removeAllViews();attempt(index+1) }
                }
            }
            runCatching { impl.banner(activity,zone,box,::callback) }.onFailure {callback("error")}
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({if(!visible)callback("error")},15000)

        }
        attempt(0)
        return wrap
    }
    fun handFinished(c: Context, session: GameSession) {
        init(c);val p=prefs?:return
        val count=GameEngine.playedHands(session);val old=p.getInt("hands_${session.id}",0)
        if(count>old)p.edit().putInt("hands_${session.id}",count).putInt("hands",p.getInt("hands",0)+count-old).apply()
    }
    fun prepare(host: MainActivity) {
        if (host.isFinishing || fullScreen) return
        val sources=chain("interstitial")
        fun attempt(index:Int) {
            if(index>=sources.size || host.isFinishing)return
            val (name,n)=sources[index];val zone=n.getJSONObject("zones").getString("interstitial")
            val impl=adapter(host,name,n)
            if(impl==null){attempt(index+1);return}
            if(impl.ready("interstitial",zone))return
            event(name,"interstitial",zone,"request")
            sdkLoad(impl,host,"interstitial",zone){ok->event(name,"interstitial",zone,if(ok)"loaded" else "error");if(!ok)attempt(index+1)}
        };attempt(0)
    }
    fun maybeInterstitial(screen: Screen) {
        val c=config()?:return;val rules=c.optJSONObject("interstitial")?:return;val p=prefs?:return
        val now=ProState.now();val day=now/86400;val count=if(p.getLong("int_day",-1)==day)p.getInt("int_count",0) else 0
        val safe=screen.adZone in setOf("home","result","leagues","history") && (screen.sessionId==null || screen.adZone=="result") && screen.host.current===screen && !screen.host.isFinishing
        if(fullScreen || rewardBusy || !NetworkAdPolicy.canInterstitial(p.getInt("hands",0),rules.optInt("hands",3),now,p.getLong("int_last",0),count,rules.optInt("daily_cap",10),safe) || now-p.getLong("int_last",0)<rules.optLong("min_seconds",180)&&p.getLong("int_last",0)>0) return
        for((name,n) in chain("interstitial")){
            val zone=n.getJSONObject("zones").getString("interstitial");val impl=adapter(screen.host,name,n)?:continue
            if(!runCatching{impl.ready("interstitial",zone)}.getOrDefault(false))continue
            fullScreen=true
            sdkShow(impl,screen.host,"interstitial",zone){kind->
                event(name,"interstitial",zone,kind)
                if(kind=="impression")p.edit().putLong("int_last",now).putLong("int_day",day).putInt("int_count",count+1).putInt("hands",0).apply()
                if(kind=="close"||kind=="error"){fullScreen=false;prepare(screen.host)}
            };return
        }
        prepare(screen.host) // Never wait for a load at a game transition.
    }
    fun reward(screen: Screen, status: (String) -> Unit, done: (Boolean) -> Unit, proReward: Boolean = false) {
        if (rewardBusy || fullScreen) {status("یک تبلیغ دیگر در حال اجراست.");done(false);return}
        if(!ProState.loggedIn){screen.host.push(AccountScreen(screen.host));done(false);return}
        val sources=chain("rewarded");if(sources.isEmpty()){status("تبلیغی موجود نیست یا در انتظار تنظیمات ناشر است.");done(false);return}
        rewardBusy=true;val activity=WeakReference(screen.host)
        status("در حال بارگذاری تبلیغ…")
        fun attempt(index:Int){
            val host=activity.get()
            if(host==null||host.isFinishing||host.current!==screen){rewardBusy=false;done(false);return}
            if(index>=sources.size){rewardBusy=false;status("اکنون تبلیغی موجود نیست؛ بعداً دوباره امتحان کنید.");done(false);return}
            val(name,n)=sources[index];val zone=n.getJSONObject("zones").getString("rewarded");val impl=adapter(host,name,n)
            if(impl==null){attempt(index+1);return}
            event(name,"rewarded",zone,"request")
            sdkLoad(impl,host,"rewarded",zone){ready->
                if(!ready){event(name,"rewarded",zone,"error");attempt(index+1)} else {
                    Api.call("POST",if(proReward)"v1/rewards/start" else "ads/reward/start",JSONObject().put("network",name).put("ad_id",0).put("request_id",id()),ProState.token){watch,err->
                        if(watch==null||host.isFinishing||host.current!==screen){rewardBusy=false;status(err?.message?:"نمایش تبلیغ ممکن نشد.");done(false)}else{
                            val watchId=watch.getString(if(proReward)"watch_token" else "watch_id");var completed=false;var ended=false
                            fullScreen=true
                            sdkShow(impl,host,"rewarded",zone){kind->
                                if(!ended){
                                    if(kind=="complete")completed=true
                                    if(kind!="complete")event(name,"rewarded",zone,kind,if(proReward)null else watchId)
                                    if(kind=="close"||kind=="error"){
                                        ended=true;fullScreen=false;rewardBusy=false
                                        if(!completed){status("تبلیغ کامل دیده نشد؛ پاداش ثبت نشد.");done(false)}else{
                                            val pending=JSONObject().put("watch_id",watchId).put("network",name).put("placement",zone).put("event_id",id()).put("purpose",if(proReward)"pro" else "coins").put("claim_after",watch.optLong("claim_after")).put("expires_at",watch.optLong("expires_at"))
                                            remember(pending)
                                            settle(host,pending){ok,message->status(message);done(ok)}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        };attempt(0)
    }
    private fun pending(uid:Long):JSONArray {
        val raw=prefs?.getString("pending_$uid",null)?:return JSONArray()
        return runCatching{if(raw.startsWith("{"))JSONArray().put(JSONObject(raw))else JSONArray(raw)}.getOrDefault(JSONArray())
    }
    private fun remember(w:JSONObject){val uid=ProState.userId;val q=pending(uid);if((0 until q.length()).none{q.getJSONObject(it).optString("watch_id")==w.optString("watch_id")})q.put(w);prefs?.edit()?.putString("pending_$uid",q.toString())?.commit()}
    private fun forget(uid:Long,id:String){val q=pending(uid);val rest=JSONArray();for(i in 0 until q.length()){val w=q.getJSONObject(i);if(w.optString("watch_id")!=id)rest.put(w)};prefs?.edit()?.putString("pending_$uid",rest.toString())?.apply()}
    /** Completion is durable before any network call. A short video may finish before the server's safety window. */
    fun completeDirect(host:MainActivity,watch:JSONObject,done:(Boolean,String)->Unit){
        init(host)
        val item=JSONObject().put("watch_id",watch.getString("watch_token")).put("purpose","pro").put("direct",true).put("claim_after",watch.optLong("claim_after")).put("expires_at",watch.optLong("expires_at"))
        remember(item);settle(host,item,done)
    }
    private val settling=HashSet<String>()
    private fun settle(host:MainActivity,pending:JSONObject,done:((Boolean,String)->Unit)?=null){
        val uid=ProState.userId;val watch=pending.getString("watch_id");val pro=pending.optString("purpose")=="pro"
        if(!settling.add(watch))return
        val expires=pending.optLong("expires_at")
        if(expires>0&&ProState.now()>expires){settling.remove(watch);forget(uid,watch);done?.invoke(false,"مهلت ثبت پاداش تمام شد؛ لطفاً با پشتیبانی تماس بگیرید.");return}
        val wait=(pending.optLong("claim_after")-ProState.now()).coerceAtLeast(0)
        if(wait>0){
            done?.invoke(false,"ویدئو کامل شد؛ ثبت جایزه در حال نهایی شدن است.")
            val weakHost=WeakReference(host)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                settling.remove(watch)
                val live=weakHost.get()
                if(live!=null&&!live.isFinishing&&ProState.userId==uid)settle(live,pending){ok,message->live.current?.kit?.toast(message);if(ok)live.refresh()}
            },wait.coerceAtMost(60)*1000+250)
            return
        }
        fun claim(){
            Api.call("POST",if(pro)"v1/rewards/claim" else "ads/reward/claim",JSONObject().put(if(pro)"watch_token" else "watch_id",watch),ProState.token){data,err->
                settling.remove(watch)
                if(ProState.userId!=uid)return@call
                if(data!=null){
                    forget(uid,watch)
                    val test=data.optBoolean("test_reward")
                    if(pro){AdEngine.event("reward","rewarded_offer",network=!pending.optBoolean("direct"));ProState.applyAccount(data)}
                    done?.invoke(true,if(test)"ویدئوی آزمایشی تکمیل شد؛ جایزهٔ واقعی پرداخت نمی‌شود." else if(pro)"یک ساعت Pro فعال شد." else "سکه‌ها به حساب شما اضافه شد.")
                } else {
                    if(err?.code in setOf("reward_limit","reward_cooldown","watch"))forget(uid,watch)
                    done?.invoke(false,err?.message?:"ثبت پاداش در انتظار اتصال است.")
                }
            }
        }
        if(pro){claim();return}
        event(pending.getString("network"),"rewarded",pending.getString("placement"),"complete",watch,pending.getString("event_id")){sent->
            if(!sent){settling.remove(watch);done?.invoke(false,"ثبت پاداش در انتظار اتصال است؛ دوباره بررسی می‌شود.");return@event}
            claim()
        }
    }
    fun recover(host:MainActivity){if(!ProState.loggedIn)return;val q=pending(ProState.userId);for(i in 0 until q.length())runCatching{settle(host,q.getJSONObject(i))}}

}

class CoinScreen(host:MainActivity):Screen(host){
    override val adZone:String get()="shop"
    override fun build():View=scaffold("سکهٔ رایگان","پاداش فقط بعد از تکمیل ویدئو ثبت می‌شود"){
        val balance=kit.text("در حال دریافت موجودی…",TextStyle.TITLE,Royal.goldLight)
        val status=kit.text("",TextStyle.LABEL,Royal.muted)
        addView(balance,kit.spaced(14));addView(status,kit.spaced(10))
        fun load(){Api.call("GET","coins/wallet",null,ProState.token){d,e->balance.text=if(d!=null)"${kit.n(d.optLong("balance"))} سکه • ${kit.n(d.optInt("rewarded_today"))} از ${kit.n(d.optInt("daily_cap"))} پاداش امروز" else e?.message?:"دریافت موجودی ممکن نشد"}}
        if(!ProState.loggedIn)addView(kit.button("ورود به حساب",ButtonKind.PRIMARY,RoyalIcon.USER){host.push(AccountScreen(host))})
        else {load();var busy=false;addView(kit.button("تماشای تبلیغ و دریافت سکه",ButtonKind.PRIMARY,RoyalIcon.PLAY){if(!busy){busy=true;AdsManager.reward(this@CoinScreen, {status.text=it}, {busy=false;load()})}})}
        AdsManager.banner(this@CoinScreen,"shop_bottom")?.let{addView(it,kit.spaced(10))}
    }
}
