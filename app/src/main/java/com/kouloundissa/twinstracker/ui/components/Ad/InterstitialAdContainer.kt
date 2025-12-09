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

    // Interstitial cache
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false

    fun preloadInterstitial(context: Context, adUnitId: String) {
        if (isInterstitialLoading || interstitialAd != null) {
            Log.d(TAG, "Interstitial: skip load (loading=${isInterstitialLoading}, hasAd=${interstitialAd != null})")
            return
        }
        Log.d(TAG, "Interstitial: start load, adUnitId=$adUnitId")
        isInterstitialLoading = true

        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            adUnitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial: onAdLoaded")
                    isInterstitialLoading = false
                    interstitialAd = ad

                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "Interstitial: onAdShowedFullScreenContent")
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.d(TAG, "Interstitial: onAdFailedToShowFullScreenContent=${adError.message}")
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
                    Log.d(TAG, "Interstitial: onAdFailedToLoad domain=${error.domain}, code=${error.code}, msg=${error.message}")
                    isInterstitialLoading = false
                    interstitialAd = null
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onNotReady: () -> Unit = {}) {
        val ad = interstitialAd
        if (ad != null) {
            Log.d(TAG, "Interstitial: show()")
            ad.show(activity)
        } else {
            Log.d(TAG, "Interstitial: show() called but ad is null")
            onNotReady()
        }
    }

    // Single place to add test devices / extras later
    fun buildAdRequest(): AdRequest =
        AdRequest.Builder().build()
}
@Composable
fun InlineBannerAd(
    adUnitId: String,
    modifier: Modifier = Modifier
        .fillMaxWidth()
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = {
            AdView(context).apply {
                // Simple banner; you can switch to adaptive later
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId

                val request = AdRequest.Builder().build()
                loadAd(request)
            }
        }
    )
}



