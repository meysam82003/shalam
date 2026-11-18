package com.meysam.divanemtiaz

/** Explicit public deployment pins. New hosts do not silently replace an APK's trust anchor. */
object LicenseTrust {
    const val CURRENT_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArgEml89iQ8gpp0+xB3OaAG3F4CdVW3Uu0bKh/RS459jiXH3/owA0AZ5WVsqbI1AUoNje7WoqBpd6vNurcy4n4vZxLz21U7PUBncgCEP++rvGCHwGB/pQcCbrpMYQK1jsJRa6OS9MVmQGiKN8B6DaO/MjsWwOL+j+817rWNpD0bS/CZxiRcTltUWPBGcGSbxUHbRlupN1oi7VDQPXKLN3YtfjlyA4pCDmkMfjQebo0pW3NSw6k9qKInomizEmE/uA7WX59QaSmpY0nhQeyrxNnXdc27JJzX0ipOnXbkfKl8g+yzujiw47hsjtzUcwOOwXDSdQvJt0wk/pQtHsoXbjpQIDAQAB"
    const val PREVIOUS_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw7JEk5pLY5jqNXNBaOf5iDrcZVgl0II2znwXp3COiBI2CophSEM2dM3lui9DZ8PALWoBVhcsJ3i5ckGMmeiXEm7t9qKeNFPNRV+1eSBDV7M0uqlLNl89tN/iC84abqkeKhR+2GDcGHSxrrNWHW565nZ0aOVNF4p2+9h7ro1jiCt+SzEFpRWSZKmfoROFqApirTVROf26A7Uisn7LZjMDUZ8oH7pB+xZYMWLN3rYCW7ov3lNZt81Tjl1jkYnT08lV+z87f0bmKpCWckplVGdKIDdko1e/HLdqnE8JtC3UTpcf4KScGYoUFLuQSKCl5m9zXfwp6QjjFDbWVmF/2Fx45wIDAQAB"
    fun parse(cert:String?,primary:String?,learned:String?,uid:Long?,device:String?):License {
        val pins=listOfNotNull(primary?.takeIf{it.isNotBlank()},CURRENT_KEY,PREVIOUS_KEY,learned?.takeIf{primary.isNullOrBlank()}).distinct()
        var failure=License.NONE
        for(pin in pins){
            val der=runCatching{B64.pemToDer(pin)}.getOrNull()?:continue
            val checked=License.parse(cert,der,uid,device)
            if(checked.reason!="signature")return checked
            failure=checked
        }
        return failure
    }
}
