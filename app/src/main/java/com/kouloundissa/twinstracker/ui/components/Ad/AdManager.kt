package com.kouloundissa.twinstracker.ui.components.Ad

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback


object AdManager {

    private const val TAG = "AdManager"


    private const val ADS_ENABLED = false
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-8151974596806893/1549328889"
    private const val INLINE_BANNER_AD_UNIT_ID = "ca-app-pub-8151974596806893/9208327057"

    //test id "ca-app-pub-3940256099942544/1033173712"
    //real id "ca-app-pub-8151974596806893/1549328889"
    // Interstitial cache
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var loadAttempts = 0
    private val maxAttempts = 3

    fun preloadInterstitial(context: Context) {
        if (interstitialAd != null || isLoading) {
            Log.d(
                TAG,
                "preloadInterstitial: skip (hasAd=${interstitialAd != null}, loading=$isLoading)"
            )
            return
        }

        if (loadAttempts >= maxAttempts) {
            Log.w(TAG, "preloadInterstitial: max attempts reached ($maxAttempts)")
            return
        }

        Log.d(TAG, "preloadInterstitial: attempt ${loadAttempts + 1}/$maxAttempts")
        isLoading = true
        loadAttempts++

        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "✅ onAdLoaded SUCCESS")
                    isLoading = false
                    loadAttempts = 0
                    interstitialAd = ad

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "Interstitial: onAdShowedFullScreenContent")
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.d(
                                TAG,
                                "Interstitial: onAdFailedToShowFullScreenContent=${adError.message}"
                            )
                            interstitialAd = null
                        }

                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Interstitial: onAdDismissedFullScreenContent")
                            interstitialAd = null
                        }

                        override fun onAdImpression() {
                            Log.d(TAG, "Interstitial: onAdImpression")
                        }

                        override fun onAdClicked() {
                            Log.d(TAG, "Interstitial: onAdClicked")
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(
                        TAG,
                        "❌ onAdFailedToLoad: code=${error.code}, domain=${error.domain}, msg=${error.message}"
                    )
                    isLoading = false
                    interstitialAd = null
                    // Retry après 2s si pas max attempts
                    if (loadAttempts < maxAttempts) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            preloadInterstitial(context)
                        }, 2000)
                    }
                }
            }
        )
    }

    fun showInterstitial(activity: Activity): Boolean {
        val ad = interstitialAd
        return if (ad != null && !isLoading) {
            Log.d(TAG, "🎬 showInterstitial: SUCCESS")
            ad.show(activity)
            true
        } else {
            Log.w(TAG, "❌ showInterstitial: ad=null (loading=$isLoading, attempts=$loadAttempts)")
            // Auto-reload si pas en cours
            if (!isLoading) {
                preloadInterstitial(activity)
            }
            false
        }
    }

    fun isAdsEnabled(): Boolean = ADS_ENABLED
    fun getInlineBannerAdUnitId(): String {
        return INLINE_BANNER_AD_UNIT_ID
        // Future: Add BuildConfig support
        // return if (BuildConfig.DEBUG) INLINE_BANNER_TEST_ID else INLINE_BANNER_AD_UNIT_ID
    }
}

@Composable
fun InlineBannerAd(
    modifier: Modifier = Modifier
        .fillMaxWidth()
) {
    val context = LocalContext.current

    if (!AdManager.isAdsEnabled()) {
        Log.d("InlineBannerAd", "Ads disabled - skipping banner")
        return
    }
    AndroidView(
        modifier = modifier,
        factory = {
            AdView(context).apply {
                // Simple banner; you can switch to adaptive later
                setAdSize(AdSize.BANNER)
                this.adUnitId =  AdManager.getInlineBannerAdUnitId()

                val request = AdRequest.Builder().build()
                loadAd(request)
            }
        }
    )
}



