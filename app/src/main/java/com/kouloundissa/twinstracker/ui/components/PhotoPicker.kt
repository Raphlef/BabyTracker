package com.kouloundissa.twinstracker.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.kouloundissa.twinstracker.R
import com.kouloundissa.twinstracker.ui.theme.BackgroundColor
import com.kouloundissa.twinstracker.ui.theme.DarkBlue
import com.kouloundissa.twinstracker.ui.theme.DarkGrey
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun PhotoPicker(
    photoUrl: Uri?,
    onPhotoSelected: (Uri?) -> Unit,
    onPhotoRemoved: () -> Unit
) {
    val contentColor = BackgroundColor
    val context = LocalContext.current

    // State to hold the URI actually displayed (remote or local)
    var displayUri by remember(photoUrl) { mutableStateOf<Uri?>(photoUrl) }
    var isLoading by remember(photoUrl) { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            if (resultUri != null) {
                scope.launch {
                    isLoading = true
                    val compressed = prepareImageForUpload(context, resultUri)
                    isLoading = false
                    onPhotoSelected(compressed ?: resultUri)
                }
            }
        }
    }
    fun launchCrop(sourceUri: Uri) {
        scope.launch {

            isLoading = true

            val localUri = ensureLocalUri(context, sourceUri)

            val destinationFile = File(
                context.cacheDir,
                "cropped_${System.currentTimeMillis()}.jpg"
            )

            val destinationUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destinationFile
            )

            val intent = UCrop.of(localUri, destinationUri)
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1080, 1080)
                .getIntent(context)

            isLoading = false
            cropLauncher.launch(intent)
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
            .background(contentColor.copy(alpha = 0.30f))
    ) {
        if (displayUri != null) {
            // Build Coil request
            val request = remember(displayUri) {
                ImageRequest.Builder(context)
                    .data(displayUri)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(true)
                    .listener(
                        onStart = {
                            isLoading = true
                            Log.d("CoilCache", "🔄 START - Key: ${it.data}")
                        },
                        onSuccess = { _request, result ->
                            isLoading = false
                            Log.d("CoilCache", "DataSource: ${result.dataSource}")
                        },
                        onError = { _, _ -> isLoading = false }
                    )
                    .build()
            }

            AsyncImage(
                model = request,
                contentDescription = "Selected Photo",
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        onClick = {
                            displayUri?.let { uri ->
                                // Convert file:// URI to content:// URI if needed
                                val shareUri = if (uri.scheme == "file") {
                                    val file = File(uri.path!!)
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                } else uri

                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(shareUri, "image/*")
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                }
                                context.startActivity(intent)
                            }
                        },
                        onLongClick = { showDialog = true }
                    ),
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
                    displayUri?.let { launchCrop(it) }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Photo",
                    tint = DarkBlue
                )
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
                    .clickable { showDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Add Photo",
                    tint = DarkGrey.copy(alpha = 0.7f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(id = R.string.select_photo_title)) },
                text = { Text(stringResource(id = R.string.select_photo_description)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDialog = false
                        PhotoFileProvider.createImageUri(context)?.also { uri ->
                            cameraUri = uri
                            cameraLauncher.launch(uri)
                        }
                    }) {
                        Text(stringResource(id = R.string.take_photo_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDialog = false
                        galleryLauncher.launch("image/*")
                    }) {
                        Text(stringResource(id = R.string.choose_from_library_button))
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
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= maxSide && halfWidth / inSampleSize >= maxSide) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

suspend fun decodeScaledBitmap(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    return withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
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

suspend fun ensureLocalUri(
    context: Context,
    uri: Uri
): Uri {
    if (uri.scheme == "http" || uri.scheme == "https") {

        val loader = ImageLoader(context)

        val request = ImageRequest.Builder(context)
            .data(uri)
            .allowHardware(false)
            .build()

        val result = loader.execute(request)
        val drawable = (result.drawable as? BitmapDrawable)
            ?: return uri

        val file = File(
            context.cacheDir,
            "remote_${System.currentTimeMillis()}.jpg"
        )

        file.outputStream().use { out ->
            drawable.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    return uri
}