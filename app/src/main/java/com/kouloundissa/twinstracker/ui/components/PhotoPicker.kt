package com.kouloundissa.twinstracker.ui.components

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.BuildConfig
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@Composable
fun PhotoPicker(
    photoUrl: Uri?,
    onPhotoSelected: (Uri?) -> Unit,
    onPhotoRemoved: () -> Unit
) {
    val contentColor = Color.White
    val context = LocalContext.current

    // State to hold the URI actually displayed (remote or local)
    var displayUri by remember(photoUrl) { mutableStateOf<Uri?>(photoUrl) }
    var isLoading by remember(photoUrl) { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Whenever photoUrl changes, ensure it's cached locally
    LaunchedEffect(photoUrl) {
        displayUri = photoUrl?.let { remoteUri ->
            val localFile = context.getExternalFilesDir("images")?.let { dir ->
                // Use URL hash as filename
                val fileName = remoteUri.toString().hashCode().toString() + ".jpg"
                File(dir, fileName)
            }
            if (localFile != null) {
                if (localFile.exists()) {
                    // Already cached: use local copy
                    localFile.toUri()
                } else {
                    // Download & save
                    isLoading = true
                    try {
                        val request = Request.Builder().url(remoteUri.toString()).build()
                        OkHttpClient().newCall(request).execute().use { resp ->
                            if (resp.isSuccessful) {
                                localFile.parentFile?.mkdirs()
                                localFile.outputStream().use { out ->
                                    resp.body?.byteStream()?.copyTo(out)
                                }
                                localFile.toUri()
                            } else remoteUri
                        }
                    } catch (e: Exception) {
                        remoteUri
                    } finally {
                        isLoading = false
                    }
                }
            } else remoteUri
        }
    }

    // Launchers for camera and gallery
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraUri != null) {
            // Compress & select
            isLoading = true
            scope.launch {
                val compressed = prepareImageForUpload(context, cameraUri!!)
                isLoading = false
                onPhotoSelected(compressed ?: cameraUri)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isLoading = true
            scope.launch {
                val compressed = prepareImageForUpload(context, uri)
                isLoading = false
                onPhotoSelected(compressed ?: uri)
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(contentColor.copy(alpha = 0.20f))
    ) {
        if (displayUri != null) {
            // Build Coil request
            val request = ImageRequest.Builder(context)
                .data(displayUri)
                .crossfade(true)
                .listener(
                    onStart = { isLoading = true },
                    onSuccess = { _, _ -> isLoading = false },
                    onError = { _, _ -> isLoading = false }
                )
                .build()

            AsyncImage(
                model = request,
                contentDescription = "Selected Photo",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showDialog = true},
                contentScale = ContentScale.Crop
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = DarkBlue)
                }
            }

            IconButton(
                onClick = {
                    onPhotoRemoved()
                    displayUri = null
                    isLoading = false
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Photo",
                    tint = Color.Red
                )
            }
        } else {
            // Placeholder to pick an image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { showDialog = true},
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Add Photo",
                    tint = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Select Photo") },
                text = { Text("Choose an option") },
                confirmButton = {
                    TextButton(onClick = {
                        showDialog = false
                        PhotoFileProvider.createImageUri(context)?.also { uri ->
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    }) {
                        Text("Take Photo")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDialog = false
                        galleryLauncher.launch("image/*")
                    }) {
                        Text("Choose from Library")
                    }
                }
            )
        }
    }
}

object PhotoFileProvider {
    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    fun createImageUri(context: Context, subfolder: String = "captured_images"): Uri? {
        val imagesDir = context.getExternalFilesDir(subfolder)?.apply { mkdirs() }
        val file = File(imagesDir, "IMG_${System.currentTimeMillis()}.jpg")
        return try {
            FileProvider.getUriForFile(context, authority(context), file)
        } catch (e: IllegalArgumentException) {
            Log.e("PhotoFileProvider", "Invalid FileProvider authority", e)
            null
        }
    }
}

private fun calculateInSampleSize(options: BitmapFactory.Options, maxSide: Int): Int {
    val (height, width) = options.outHeight to options.outWidth
    var inSampleSize = 1
    if (height > maxSide || width > maxSide) {
        val halfHeight = height / 2
        val halfWidth  = width / 2
        while (halfHeight / inSampleSize >= maxSide && halfWidth / inSampleSize >= maxSide) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

suspend fun decodeScaledBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    return withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val sampleSize = calculateInSampleSize(options, maxSide)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    }
}

suspend fun compressBitmapToFile(
    context: Context,
    bitmap: Bitmap,
    quality: Int = 80,
    subfolder: String = "uploads"
): Uri? = withContext(Dispatchers.IO) {
    val uploadDir = context.cacheDir.resolve(subfolder).apply { mkdirs() }
    val file = File(uploadDir, "IMG_${System.currentTimeMillis()}.jpg")
    return@withContext try {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        file.toUri()
    } catch (e: Exception) {
        null
    }
}

suspend fun prepareImageForUpload(
    context: Context,
    sourceUri: Uri,
    maxSide: Int = 1080,
    jpegQuality: Int = 80
): Uri? {
    val bitmap = decodeScaledBitmap(context, sourceUri, maxSide) ?: return null
    return compressBitmapToFile(context, bitmap, jpegQuality)
}