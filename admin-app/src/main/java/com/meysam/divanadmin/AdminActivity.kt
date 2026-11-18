package com.meysam.divanadmin

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.Base64
import android.security.KeyPairGeneratorSpec
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.math.BigInteger
import java.util.Calendar
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.security.auth.x500.X500Principal

/** Dedicated native app. No browser, WebView, SDK or user-game data lives in this package. */
class AdminActivity:Activity(){
    private val io=Executors.newSingleThreadExecutor()
    private val store by lazy{getSharedPreferences("admin",MODE_PRIVATE)}
    private val bg=Color.rgb(11,18,32);private val panel=Color.rgb(19,31,48);private val gold=Color.rgb(232,186,105);private val ink=Color.rgb(237,241,248);private val muted=Color.rgb(171,188,209)
    private var token:String?=null;private var generation=0;private var current="dashboard"
    private lateinit var body:LinearLayout
    private val font by lazy{Typeface.createFromAsset(assets,"Vazirmatn-Regular.ttf")}
    private fun dp(n:Int)=(n*resources.displayMetrics.density+.5f).toInt()
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);window.statusBarColor=bg;window.navigationBarColor=bg;token=readToken();if(token==null)login() else if(intent?.getBooleanExtra("inbox",false)==true)inbox()else home()}
    private fun shape(color:Int=panel)=GradientDrawable().apply{setColor(color);cornerRadius=dp(16).toFloat();setStroke(dp(1),Color.rgb(55,72,94))}
    private fun column()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
    private fun text(value:String,size:Float=15f,color:Int=ink)=TextView(this).apply{text=value;textSize=size;setTextColor(color);typeface=font;setPadding(dp(3),dp(5),dp(3),dp(5))}
    private fun button(label:String,action:()->Unit)=TextView(this).apply{text=label;textSize=15f;typeface=font;setTextColor(gold);gravity=Gravity.CENTER;minimumHeight=dp(50);background=shape();setPadding(dp(12),dp(10),dp(12),dp(10));isClickable=true;setOnClickListener{action()};layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)}}
    private fun edit(value:String="",secret:Boolean=false)=EditText(this).apply{setText(value);setTextColor(ink);setHintTextColor(muted);textSize=15f;typeface=font;setSingleLine(true);background=shape();setPadding(dp(12),dp(10),dp(12),dp(10));inputType=if(secret)InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT}
    private fun page(title:String){generation++;val root=column().apply{setBackgroundColor(bg);fitsSystemWindows=true};val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(14),dp(8),dp(14),dp(8));layoutDirection=View.LAYOUT_DIRECTION_RTL};bar.addView(text(title,22f,gold),LinearLayout.LayoutParams(0,-2,1f));if(token!=null)bar.addView(button("خانه"){home()},LinearLayout.LayoutParams(dp(68),dp(48)));root.addView(bar);body=column().apply{setPadding(dp(18),dp(12),dp(18),dp(28))};root.addView(ScrollView(this).apply{isFillViewport=true;addView(body)},LinearLayout.LayoutParams(-1,0,1f));setContentView(root)}
    private fun message(s:String){if(!isFinishing)AlertDialog.Builder(this).setMessage(s).setPositiveButton("باشه",null).show()}
    private fun base():String{val v=store.getString("url","https://divan-emtiaz.ir/divan/api/index.php")!!;val u=URL(v);require(u.protocol=="https"&&u.userInfo==null&&u.query==null&&u.ref==null){"نشانی باید HTTPS و بدون پارامتر باشد."};return v}
    private fun api(method:String,route:String,data:JSONObject?=null,files:List<Triple<String,android.net.Uri,String>> = emptyList(),done:(JSONObject)->Unit){
        val gen=generation;io.execute{
            var result:JSONObject?=null;var error:String?=null;var unauthorized=false
            try{val u=URL(base()+"?r="+URLEncoder.encode(route,"UTF-8"));val c=u.openConnection() as HttpURLConnection;com.meysam.divanpush.NativeTls.apply(applicationContext,c)
                try{c.requestMethod=method;c.instanceFollowRedirects=false;c.connectTimeout=12000;c.readTimeout=25000;c.setRequestProperty("Accept","application/json");token?.let{c.setRequestProperty("Authorization","Bearer $it")};if(method!="GET"){
                    c.doOutput=true
                    if(files.isEmpty()){c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use{it.write((data?:JSONObject()).toString().toByteArray(Charsets.UTF_8))}}
                    else {
                        val boundary="Divan"+java.util.UUID.randomUUID().toString();c.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary")
                        c.setChunkedStreamingMode(65536)
                        c.outputStream.use{out->fun write(v:String){out.write(v.toByteArray(Charsets.UTF_8))}
                            write("--$boundary\r\nContent-Disposition: form-data; name=\"native_payload\"\r\n\r\n"+(data?:JSONObject()).toString()+"\r\n")
                            for((field,uri,fileName)in files){require(!field.contains('"')&&!field.contains('\r')&&!field.contains('\n'));val name=fileName.replace(Regex("[\"\r\n]"),"_");write("--$boundary\r\nContent-Disposition: form-data; name=\"$field\"; filename=\"$name\"\r\nContent-Type: application/octet-stream\r\n\r\n");contentResolver.openInputStream(uri)!!.use{it.copyTo(out,65536)};write("\r\n")}
                            write("--$boundary--\r\n")
                        }
                    }
                }
                    val status=c.responseCode;val raw=(if(status<400)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}?:"";val j=JSONObject(raw);if(j.optBoolean("ok"))result=j.getJSONObject("data")else{error=j.optJSONObject("error")?.optString("message")?:"درخواست انجام نشد";unauthorized=status==401&&route!="admin/mobile/login"&&route!="admin/mobile/verify"}
                }finally{c.disconnect()}
            }catch(e:Exception){error=e.message?:"ارتباط با سرور برقرار نشد"}
            runOnUiThread{if(isFinishing||isDestroyed||generation!=gen)return@runOnUiThread;if(unauthorized){saveToken(null);token=null;login()}else if(result!=null)done(result!!) else message(error?:"خطای ارتباط")}
        }
    }
    private fun login(){current="login";page("ورود به مدیریت دیوان");body.addView(text("همان حساب و کد دومرحله‌ای پنل",15f,muted));body.addView(text("نشانی API پنل"));val url=edit(store.getString("url","https://divan-emtiaz.ir/divan/api/index.php")!!);body.addView(url);body.addView(text("نام کاربری مدیر"));val user=edit();body.addView(user);body.addView(text("رمز عبور"));val pass=edit(secret=true);body.addView(pass);body.addView(button("ادامه"){store.edit().putString("url",url.text.toString().trim()).apply();api("POST","admin/mobile/login",JSONObject().put("username",user.text.toString()).put("password",pass.text.toString())){d->pass.text.clear();verify(d.getString("challenge"))}})}
    private fun verify(challenge:String){page("تأیید دومرحله‌ای");body.addView(text("کد برنامهٔ احراز هویت را وارد کنید."));val code=edit();code.inputType=InputType.TYPE_CLASS_NUMBER;body.addView(code);body.addView(button("ورود"){api("POST","admin/mobile/verify",JSONObject().put("challenge",challenge).put("code",code.text.toString())){d->token=d.getString("token");saveToken(token);store.edit().remove("inbox_cursor").apply();AdminNotifications.schedule(this);com.meysam.divanpush.PushRuntime.connect(this,getSharedPreferences("admin",MODE_PRIVATE).getString("url","https://divan-emtiaz.ir/divan/api/index.php")!!,"admin");io.execute{runCatching{AdminNotifications.sync(applicationContext)}};if(android.os.Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),73);foreground.post(checkEvents);home()}});body.addView(button("شروع دوباره"){login()})}
    private fun home(){current="dashboard";page("مدیریت دیوان");api("GET","admin/mobile/dashboard"){d->body.addView(text(d.optString("username"),18f,gold));items(d)
        body.addView(button("رویدادهای ثبت‌نام و خرید"){inbox()})
        body.addView(Switch(this).apply{text="اعلان ثبت‌نام و خرید";setTextColor(ink);typeface=font;isChecked=store.getBoolean("notifications",true);setOnCheckedChangeListener{_,on->store.edit().putBoolean("notifications",on).apply();com.meysam.divanpush.PushRuntime.updateRegistration(this@AdminActivity);if(on&&android.os.Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),73)}})
        body.addView(button("آمار و پاداش سکه‌ها"){open("stats")})
        api("GET","admin/mobile/menu"){menu->val list=menu.getJSONArray("items");for(i in 0 until list.length()){val item=list.getJSONObject(i);body.addView(button(item.getString("title")){nativePage("p="+item.getString("key"))})}}
        body.addView(button("خروج از حساب"){api("POST","admin/mobile/logout"){token=null;saveToken(null);login()}})
    }}
    private fun open(kind:String){current=kind;page(when(kind){"ads"->"مدیریت تبلیغات";"stats"->"گزارش تبلیغات";"notices"->"اعلان‌ها";"gifts"->"هدیهٔ کاربران";else->"مدیریت دیوان"})
        if(kind=="stats"){api("GET","admin/ads/stats"){stats(it)};return}
        val route=if(kind=="ads")"admin/ads/settings" else "admin/mobile/$kind"
        api("GET",route){d->d.optString("message").takeIf{it.isNotBlank()}?.let{body.addView(text(it,13f,muted))};d.optJSONArray("fields")?.let{form(it,route,if(kind=="ads")"PUT" else "POST")};items(d,kind)}
    }
    private var chooseFile:((android.net.Uri,String)->Unit)?=null
    private var saveFile:JSONObject?=null
    private var panelQuery="p=dashboard"
    private fun nativePage(query:String,verb:String="GET",fields:JSONArray=JSONArray(),files:List<Triple<String,android.net.Uri,String>> = emptyList()){
        current="panel";panelQuery=query;page("مدیریت دیوان")
        api("POST","admin/mobile/panel",JSONObject().put("query",query).put("verb",verb).put("fields",fields),files){d->
            if(d.optBoolean("logout")){token=null;saveToken(null);login();return@api}
            panelQuery=d.optString("query",query)
            d.optJSONObject("file")?.let{file->saveFile=file;startActivityForResult(android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE).setType(file.optString("mime","application/octet-stream")).putExtra(android.content.Intent.EXTRA_TITLE,file.optString("name","divan-export")),72)}
            val nodes=d.optJSONArray("nodes")?:JSONArray();for(i in 0 until nodes.length())node(nodes.getJSONObject(i))
        }
    }
    private fun link(href:String){
        if(href.startsWith("#"))return
        val raw=android.net.Uri.parse(href)
        if(raw.scheme==null && (raw.path.isNullOrBlank()||raw.path?.endsWith("index.php")==true)){nativePage(raw.encodedQuery?:"p=dashboard");return}
        val resolved=runCatching { URL(URL(base()),"../admin/"+href).takeIf{raw.scheme==null}?:URL(href) }.getOrNull()?:return
        if(resolved.protocol!="https"||resolved.userInfo!=null)return
        val own=URL(base())
        if(resolved.host==own.host&&resolved.path.endsWith("/admin/index.php")){nativePage(resolved.query?:"p=dashboard");return}
        runCatching{startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(resolved.toString())))}
    }
    private fun image(src:String){
        val view=ImageView(this).apply{adjustViewBounds=true;contentDescription="پیش‌نمایش رسانه"}
        body.addView(view,LinearLayout.LayoutParams(-1,dp(210)))
        val epoch=generation
        io.execute {
            val bitmap=runCatching {
                val bytes=if(src.startsWith("data:image/")&&src.contains("base64,")){
                    require(src.length<2_000_000);Base64.decode(src.substringAfter("base64,"),Base64.DEFAULT)
                }else{
                    val own=URL(base());val u=URL(URL(own,"../admin/"),src)
                    require(u.protocol=="https"&&u.host==own.host&&u.userInfo==null)
                    val conn=u.openConnection() as HttpURLConnection;com.meysam.divanpush.NativeTls.apply(applicationContext,conn);conn.instanceFollowRedirects=false;conn.connectTimeout=8000;conn.readTimeout=10000
                    try {require(conn.responseCode==200);conn.inputStream.use { input ->
                        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var total=0
                        while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=3_000_000);out.write(buffer,0,n)};out.toByteArray()
                    }}finally{conn.disconnect()}
                }
                val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                var sample=1;while(bounds.outWidth/sample>1080||bounds.outHeight/sample>1080)sample*=2
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,android.graphics.BitmapFactory.Options().apply{inSampleSize=sample})
            }.getOrNull()
            runOnUiThread {if(!isFinishing&&!isDestroyed&&epoch==generation){if(bitmap!=null)view.setImageBitmap(bitmap)else view.visibility=View.GONE}}
        }
    }
    private fun node(n:JSONObject){when(n.optString("kind")){
        "heading"->body.addView(text(n.optString("text"),20f,gold))
        "text"->body.addView(text(n.optString("text"),14f,muted).apply{setTextIsSelectable(true)})
        "divider"->body.addView(View(this).apply{setBackgroundColor(Color.rgb(51,67,85))},LinearLayout.LayoutParams(-1,dp(1)).apply{topMargin=dp(8);bottomMargin=dp(8)})
        "link"->body.addView(button(n.optString("text")){link(n.optString("href"))})
        "form"->nativeForm(n)
        "image"->image(n.optString("src"))
    }}
    private fun nativeForm(form:JSONObject){
        val box=column().apply{background=shape();setPadding(dp(12),dp(12),dp(12),dp(14));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(14)}}
        val caption=form.optString("caption");if(caption.isNotBlank())box.addView(text(caption,15f,gold))
        val getters=ArrayList<()->List<Pair<String,String>>>()
        val uploads=HashMap<String,Pair<android.net.Uri,String>>()
        val radios=HashMap<String,MutableList<CheckBox>>()
        val fields=form.optJSONArray("fields")?:JSONArray()
        for(i in 0 until fields.length()){
            val f=fields.getJSONObject(i);val name=f.getString("name");val value=f.optString("value");val type=f.optString("type");val label=f.optString("label")
            when(type){
                "hidden"->{getters+={listOf(name to value)}}
                "checkbox","radio"->{val v=CheckBox(this).apply{text=label;setTextColor(ink);typeface=font;isChecked=f.optBoolean("checked")};if(type=="radio"){val group=radios.getOrPut(name){ArrayList()};group+=v;v.setOnCheckedChangeListener{_,on->if(on)group.filter{it!==v}.forEach{it.isChecked=false}}};box.addView(v);getters+={if(v.isChecked)listOf(name to value.ifEmpty{"on"})else emptyList()}}
                "select","multi"->{box.addView(text(label,14f,muted));val opts=f.optJSONArray("options")?:JSONArray();val values=(0 until opts.length()).map{opts.getJSONObject(it)}
                    if(type=="multi"){val selected=HashSet<String>();for(o in values){val v=CheckBox(this).apply{text=o.optString("label");setTextColor(ink);typeface=font;isChecked=o.optBoolean("selected")};if(v.isChecked)selected+=o.optString("value");v.setOnCheckedChangeListener{_,on->if(on)selected+=o.optString("value")else selected-=o.optString("value")};box.addView(v)};getters+={selected.map{name to it}}}
                    else{val v=Spinner(this);v.adapter=object:ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values.map{it.optString("label")}){override fun getView(position:Int,convertView:View?,parent:android.view.ViewGroup):View=super.getView(position,convertView,parent).apply{(this as TextView).setTextColor(ink);typeface=font}};v.setSelection(values.indexOfFirst{it.optBoolean("selected")}.coerceAtLeast(0));box.addView(v);getters+={if(values.isEmpty())emptyList()else listOf(name to values[v.selectedItemPosition].optString("value"))}}
                }
                "file"->{val info=text("فایلی انتخاب نشده",12f,muted);box.addView(text(label));box.addView(button("انتخاب فایل"){chooseFile={uri,fileName->uploads[name]=uri to fileName;info.text=fileName};startActivityForResult(android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*"),71)});box.addView(info)}
                else->{box.addView(text(label,14f,muted));val input=edit(value,type=="password");input.hint=f.optString("placeholder");if(type=="textarea"){input.setSingleLine(false);input.minLines=3;input.gravity=Gravity.TOP};if(type=="number")input.inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED;input.isEnabled=!f.optBoolean("readonly");box.addView(input);getters+={listOf(name to input.text.toString())}}
            }
        }
        val actions=form.optJSONArray("buttons")?:JSONArray()
        fun submit(action:JSONObject){
            val pairs=getters.flatMap{it()}.toMutableList();if(action.optString("name").isNotBlank())pairs+=action.getString("name") to action.optString("value")
            val actionPath=form.optString("action");val query=if(actionPath.isBlank())panelQuery else android.net.Uri.parse(actionPath).encodedQuery?:panelQuery
            if(form.optString("method")=="GET"){val encoded=pairs.joinToString("&"){URLEncoder.encode(it.first,"UTF-8")+"="+URLEncoder.encode(it.second,"UTF-8")};nativePage(query+"&"+encoded)}
            else{val data=JSONArray();pairs.forEach{(name,value)->data.put(JSONObject().put("name",name).put("value",value))};nativePage(query,"POST",data,uploads.map{(name,pair)->Triple(name,pair.first,pair.second)})}
        }
        for(i in 0 until actions.length()){val action=actions.getJSONObject(i);box.addView(button(action.optString("text","ذخیره")){if(action.optBoolean("confirm"))AlertDialog.Builder(this).setMessage("این تغییر اعمال شود؟").setNegativeButton("انصراف",null).setPositiveButton("اعمال"){_,_->submit(action)}.show() else submit(action)})}
        body.addView(box)
    }
    @Deprecated("Activity result compatibility") override fun onActivityResult(requestCode:Int,resultCode:Int,data:android.content.Intent?){super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK)return;val uri=data?.data?:return
        if(requestCode==71){var name="upload";contentResolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())name=it.getString(0)};chooseFile?.invoke(uri,name);chooseFile=null}
        if(requestCode==72){val file=saveFile?:return;saveFile=null;io.execute{try{contentResolver.openOutputStream(uri)!!.use{out->if(file.has("data"))out.write(Base64.decode(file.getString("data"),Base64.DEFAULT))else{val url=URL(file.getString("url"));require(url.protocol=="https"&&url.host==URL(base()).host);val c=url.openConnection()as HttpURLConnection;com.meysam.divanpush.NativeTls.apply(applicationContext,c);c.instanceFollowRedirects=false;c.setRequestProperty("Authorization","Bearer $token");c.connectTimeout=12000;c.readTimeout=60000;try{require(c.responseCode==200);c.inputStream.use{it.copyTo(out,65536)}}finally{c.disconnect()}}};runOnUiThread{message("فایل ذخیره شد.")}}catch(e:Exception){runOnUiThread{message("دریافت فایل انجام نشد؛ دوباره تلاش کنید.")}}}}
    }
    private fun inbox(){current="inbox";page("رویدادهای مدیریت");api("GET","admin/mobile/inbox"){d->val entries=d.optJSONArray("items")?:JSONArray();if(entries.length()==0)body.addView(text("رویدادی ثبت نشده است.",15f,muted));for(i in 0 until entries.length()){val v=entries.getJSONObject(i);body.addView(text(v.optString("title"),18f,gold));body.addView(text(v.optString("body"),14f,muted))};api("POST","admin/mobile/inbox",JSONObject().put("read_through",d.optLong("latest_id"))){} }}
    private fun put(root:JSONObject,path:String,value:Any){val parts=path.split('.');var cursor=root;for(k in parts.dropLast(1)){var child=cursor.optJSONObject(k);if(child==null){child=JSONObject();cursor.put(k,child)};cursor=child};cursor.put(parts.last(),value)}
    private fun form(fields:JSONArray,route:String,method:String){val getters=ArrayList<Pair<String,()->Any>>();body.addView(text(if(current=="ads"||current=="contacts")"تنظیمات" else "ثبت مورد جدید",19f,gold))
        for(i in 0 until fields.length()){val f=fields.getJSONObject(i);val key=f.getString("key");val type=f.optString("type");val label=f.getString("label")
            if(type=="bool"){val box=Switch(this).apply{text=label;setTextColor(ink);typeface=font;isChecked=f.optBoolean("value");setPadding(dp(4),dp(9),dp(4),dp(9))};body.addView(box);getters+=key to {box.isChecked}}
            else if(type=="select"){body.addView(text(label));val values=f.getJSONArray("options").let{a->(0 until a.length()).map{a.getString(it)}};val spinner=Spinner(this);spinner.adapter=object:ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values){override fun getView(position:Int,convertView:View?,parent:android.view.ViewGroup):View=super.getView(position,convertView,parent).apply{(this as TextView).setTextColor(ink);typeface=font}};spinner.setSelection(values.indexOf(f.optString("value")).coerceAtLeast(0));body.addView(spinner);getters+=key to {values[spinner.selectedItemPosition]}}
            else{body.addView(text(label));val initial=if(type=="list")f.optJSONArray("value")?.let{a->(0 until a.length()).joinToString(","){a.getString(it)}}?:"" else f.optString("value");val input=edit(initial);if(type=="number")input.inputType=InputType.TYPE_CLASS_NUMBER;body.addView(input);getters+=key to {when(type){"number"->input.text.toString().toInt();"list"->JSONArray(input.text.toString().split(',').map{it.trim()}.filter{it.isNotEmpty()});else->input.text.toString()}}}
        }
        body.addView(button("ذخیره و اعمال"){try{val data=JSONObject();getters.forEach{(key,get)->put(data,key,get())};api(method,route,data){open(current)}}catch(e:Exception){message("عدد یا مقدار واردشده معتبر نیست.")}})
    }
    private fun items(data:JSONObject,kind:String=""){val items=data.optJSONArray("items")?:return;if(items.length()==0)body.addView(text("هنوز موردی ثبت نشده است.",15f,muted));for(i in 0 until items.length()){val v=items.getJSONObject(i);val card=column().apply{background=shape();setPadding(dp(12),dp(9),dp(12),dp(12));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(10)}};card.addView(text(v.optString("title"),17f,gold));card.addView(text(v.optString("body"),13f,muted));if(kind=="notices"||kind=="gifts"){val active=v.optBoolean("active");if(kind=="notices"||active)card.addView(button(if(kind=="gifts")"لغو هدیه" else if(active)"غیرفعال کردن" else "فعال کردن"){AlertDialog.Builder(this).setMessage("تغییر اعمال شود؟").setNegativeButton("انصراف",null).setPositiveButton("اعمال"){_,_->api("POST","admin/mobile/$kind",JSONObject().put(if(kind=="gifts")"revoke_id" else "toggle_id",v.getInt("id"))){open(kind)}}.show()})};body.addView(card)}}
    private fun stats(data: JSONObject) {
        body.addView(text("درآمد واقعی: " + data.optString("revenue_label", "ناموجود"), 21f, gold))
        body.addView(text(data.optString("verification"), 13f, muted))
        val events = data.optJSONArray("events") ?: JSONArray()
        for (i in 0 until events.length()) {
            val event = events.getJSONObject(i)
            val suffix = if (event.optInt("test_mode") == 1) " (آزمایشی)" else ""
            body.addView(text(event.optString("network") + " — " + event.optString("event") + ": " + event.optLong("total") + suffix))
        }
        body.addView(text("پاداش‌های پرداخت‌شده", 19f, gold))
        val history = data.optJSONArray("history") ?: JSONArray()
        for (i in 0 until history.length()) {
            val row = history.getJSONObject(i)
            body.addView(text(row.optString("username") + " — " + row.optInt("amount") + " سکه — " + row.optString("created_at")))
        }
    }
    private fun saveToken(value:String?){AdminSessionVault.save(this,value)}
    private fun readToken():String?=AdminSessionVault.read(this)
    private val foreground=android.os.Handler(android.os.Looper.getMainLooper())
    private val checkEvents=object:Runnable{override fun run(){if(token!=null&&!isFinishing){io.execute{runCatching{AdminNotifications.sync(applicationContext)}};foreground.postDelayed(this,60000)}}}
    override fun onResume(){super.onResume();if(token!=null){AdminNotifications.schedule(this);com.meysam.divanpush.PushRuntime.connect(this,getSharedPreferences("admin",MODE_PRIVATE).getString("url","https://divan-emtiaz.ir/divan/api/index.php")!!,"admin");foreground.removeCallbacks(checkEvents);foreground.post(checkEvents)}}
    override fun onPause(){foreground.removeCallbacks(checkEvents);super.onPause()}
    override fun onNewIntent(intent:android.content.Intent?){super.onNewIntent(intent);if(intent?.getBooleanExtra("inbox",false)==true&&token!=null)inbox()}
    @Deprecated("Android back compatibility") override fun onBackPressed(){if(token==null)super.onBackPressed() else if(current!="dashboard")home()else super.onBackPressed()}
    override fun onDestroy(){foreground.removeCallbacks(checkEvents);io.shutdownNow();super.onDestroy()}
}
