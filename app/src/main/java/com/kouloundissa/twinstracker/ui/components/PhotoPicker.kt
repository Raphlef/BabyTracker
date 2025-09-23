package com.kouloundissa.twinstracker.ui.components

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Composable
fun PhotoPicker(
    photoUrl: Uri?,
    onPhotoSelected: (Uri?) -> Unit,
    onPhotoRemoved: () -> Unit
) {
    val context = LocalContext.current

    // State to hold the URI actually displayed (remote or local)
    var displayUri by remember(photoUrl) { mutableStateOf<Uri?>(photoUrl) }
    var isLoading by remember(photoUrl) { mutableStateOf(false) }

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

    // Image picker launcher
    val chooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
            ?: result.data?.clipData?.getItemAt(0)?.uri
        onPhotoSelected(uri)
    }
    val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
    val openDocIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "image/*"
    }
    val chooserIntent = Intent.createChooser(pickIntent, "Select Photo").apply {
        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(openDocIntent))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
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
                    .clickable { chooserLauncher.launch(chooserIntent) },
                contentScale = ContentScale.Crop
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else {
            // Placeholder to pick an image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { chooserLauncher.launch(chooserIntent) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Add Photo",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}
