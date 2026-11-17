package com.meysam.divanemtiaz
import org.junit.Assert.*
import org.junit.Test
class NetworkAdsTest {
    @Test fun onlyNaturalBreaksWithThreeHandsAndCooldown(){
        assertFalse(NetworkAdPolicy.canInterstitial(2,3,200,0,0,10,true))
        assertFalse(NetworkAdPolicy.canInterstitial(3,3,200,0,0,10,false))
        assertTrue(NetworkAdPolicy.canInterstitial(3,3,200,0,0,10,true))
        assertFalse(NetworkAdPolicy.canInterstitial(3,3,300,200,0,10,true))
        assertTrue(NetworkAdPolicy.canInterstitial(3,3,380,200,0,10,true))
        assertFalse(NetworkAdPolicy.canInterstitial(3,3,600,200,10,10,true))
    }
    @Test fun bannerNeverOverlaysPlayOrUnconfiguredPages(){
        val pages=setOf("home","shop")
        assertTrue(NetworkAdPolicy.canBanner("home",false,false,false,pages))
        assertFalse(NetworkAdPolicy.canBanner("home",true,false,false,pages))
        assertFalse(NetworkAdPolicy.canBanner("home",false,true,false,pages))
        assertFalse(NetworkAdPolicy.canBanner(null,false,false,false,pages))
        assertFalse(NetworkAdPolicy.canBanner("mafia_night",false,false,false,pages))
        assertTrue(NetworkAdPolicy.canBanner("shop",false,true,true,pages))
    }
}
