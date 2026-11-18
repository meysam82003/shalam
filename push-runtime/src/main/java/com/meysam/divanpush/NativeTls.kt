package com.meysam.divanpush

import android.content.Context
import android.os.Build
import java.net.HttpURLConnection
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/** Same additional public ISRG trust roots as the existing player app, for Android 5/6. Never trusts all. */
object NativeTls {
    @Volatile private var factory:SSLSocketFactory?=null
    fun apply(context:Context,connection:HttpURLConnection){
        if(Build.VERSION.SDK_INT>=25 || connection !is HttpsURLConnection)return
        val chosen=factory?:build(context.applicationContext)?.also{factory=it}?:return
        connection.sslSocketFactory=chosen // The platform hostname verifier remains unchanged.
    }
    private fun build(context:Context):SSLSocketFactory?=runCatching{
        val roots=java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType()).apply{load(null,null)}
        val system=java.security.KeyStore.getInstance("AndroidCAStore").apply{load(null,null)}
        system.aliases().toList().forEach{alias->system.getCertificate(alias)?.let{roots.setCertificateEntry("system-$alias",it)}}
        val certificates=java.security.cert.CertificateFactory.getInstance("X.509")
        context.assets.list("certs")?.forEach{name->context.assets.open("certs/$name").use{roots.setCertificateEntry("bundled-$name",certificates.generateCertificate(it))}}
        val manager=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()).apply{init(roots)}
        javax.net.ssl.SSLContext.getInstance("TLS").apply{init(null,manager.trustManagers,null)}.socketFactory
    }.getOrNull()
}
