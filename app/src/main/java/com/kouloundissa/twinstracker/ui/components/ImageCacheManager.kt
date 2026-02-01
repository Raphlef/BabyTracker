package com.kouloundissa.twinstracker.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Manages image caching with persistent storage
 * Images are downloaded once and reused across app restarts
 */
object ImageCacheManager {
    private const val TAG = "ImageCache"
    private const val CACHE_DIR = "images"

    // Reuse OkHttp client for better performance
    private val httpClient = OkHttpClient()

    /**
     * Gets cached image URI, downloading if necessary
     * Always runs on IO dispatcher - safe to call from any context
     *
     * @param context Application context
     * @param remoteUrl Remote image URL
     * @return Local cached URI or original remote URI on failure
     */
    suspend fun getCachedImageUri(
        context: Context,
        remoteUrl: String
    ): Uri = withContext(Dispatchers.IO) {
        val localFile = getCacheFile(context, remoteUrl)

        if (localFile == null) {
            Log.e(TAG, "✗ Cannot access storage directory")
            return@withContext Uri.parse(remoteUrl)
        }

        // Check if already cached
        if (localFile.exists()) {
            Log.d(TAG, "✓ Cache HIT: ${localFile.name} (${localFile.length() / 1024}KB)")
            return@withContext localFile.toUri()
        }

        // Download and cache
        Log.d(TAG, "✗ Cache MISS: Downloading $remoteUrl")
        downloadAndCache(remoteUrl, localFile) ?: Uri.parse(remoteUrl)
    }

    /**
     * Gets cache file path for a given URL
     */
    private fun getCacheFile(context: Context, url: String): File? {
        val cacheDir = context.getExternalFilesDir(CACHE_DIR) ?: return null
        val fileName = "${url.hashCode()}.jpg"
        return File(cacheDir, fileName)
    }

    /**
     * Downloads image and saves to local file
     */
    private fun downloadAndCache(url: String, destination: File): Uri? = try {
        val request = Request.Builder().url(url).build()

        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful && response.body != null) {
                // Ensure parent directory exists
                destination.parentFile?.mkdirs()

                // Save to file
                destination.outputStream().use { outputStream ->
                    response.body!!.byteStream().copyTo(outputStream)
                }

                Log.i(TAG, "✓ Downloaded: ${destination.name} (${destination.length() / 1024}KB)")
                Log.i(TAG, "  Path: ${destination.absolutePath}")
                destination.toUri()
            } else {
                Log.e(TAG, "✗ HTTP ${response.code}: $url")
                null
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "✗ Download failed: ${e.message}", e)
        null
    }

    /**
     * Clears all cached images
     * @return Number of files deleted
     */
    suspend fun clearCache(context: Context): Int = withContext(Dispatchers.IO) {
        val cacheDir = context.getExternalFilesDir(CACHE_DIR) ?: return@withContext 0
        val files = cacheDir.listFiles() ?: return@withContext 0

        var deletedCount = 0
        files.forEach { file ->
            if (file.delete()) {
                deletedCount++
            }
        }

        Log.i(TAG, "Cache cleared: $deletedCount files deleted")
        deletedCount
    }

    /**
     * Gets total cache size in bytes
     */
    suspend fun getCacheSize(context: Context): Long = withContext(Dispatchers.IO) {
        val cacheDir = context.getExternalFilesDir(CACHE_DIR) ?: return@withContext 0L
        cacheDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }
}