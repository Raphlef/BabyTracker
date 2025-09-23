package com.kouloundissa.twinstracker.ui.components

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
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun PhotoPicker(
    photoUrl: Uri?,
    onPhotoSelected: (Uri?) -> Unit,
    onPhotoRemoved: () -> Unit
) {
    // Track loading state; reset whenever photoUrl changes
    var isLoading by remember(photoUrl) { mutableStateOf(false) }


    val chooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
            ?: result.data?.clipData?.let { it.getItemAt(0).uri }
        onPhotoSelected(uri)
    }

    val context = LocalContext.current
    val pickImageIntent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
    val openDocIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "image/*"
    }
    val chooserIntent = Intent.createChooser(pickImageIntent, "Select Photo").apply {
        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(openDocIntent))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        if (photoUrl != null) {
            // Load image with progress callbacks
            val imageRequest = ImageRequest.Builder(context)
                .data(photoUrl)
                .crossfade(true)
                .listener(
                    onStart = { isLoading = true },
                    onSuccess = { _, _ -> isLoading = false },
                    onError = { _, _ -> isLoading = false }
                )
                .build()
            // Display the image
            AsyncImage(
                model = imageRequest,
                contentDescription = "Selected Photo",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { chooserLauncher.launch(chooserIntent) },
                contentScale = ContentScale.Crop
            )
            // Overlay: show progress while loading
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // Overlay delete icon
            IconButton(
                onClick = {
                    onPhotoRemoved()
                    isLoading = false
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
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
            // Placeholder: tap anywhere to pick
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