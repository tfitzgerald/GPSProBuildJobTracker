@file:OptIn(ExperimentalMaterial3Api::class)

package ca.gpsprobuild.app.ui.screens.photos

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ca.gpsprobuild.app.core.util.Dates
import ca.gpsprobuild.app.data.local.entity.PhotoEntity
import ca.gpsprobuild.app.data.repository.PhotoRepository
import ca.gpsprobuild.app.domain.model.PhotoCategory
import ca.gpsprobuild.app.ui.components.SectionHeader
import ca.gpsprobuild.app.ui.components.StatusChip
import ca.gpsprobuild.app.ui.theme.Dimens
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PhotoTabUiState(
    val photos: List<PhotoEntity> = emptyList(),
    val filter: PhotoCategory? = null,
    val busy: Boolean = false
) {
    val visible: List<PhotoEntity>
        get() = filter?.let { f -> photos.filter { it.category == f } } ?: photos

    val usedCategories: List<PhotoCategory>
        get() = photos.map { it.category }.distinct().sortedBy { it.ordinal }
}

@HiltViewModel
class PhotoTabViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PhotoRepository
) : ViewModel() {

    private val jobId: Long = savedStateHandle.get<String>("jobId")?.toLongOrNull() ?: 0L
    private val filter = MutableStateFlow<PhotoCategory?>(null)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<PhotoTabUiState> = combine(
        repository.observeForJob(jobId), filter, busy
    ) { photos, f, isBusy ->
        PhotoTabUiState(photos, f, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PhotoTabUiState())

    fun setFilter(category: PhotoCategory?) = filter.update { category }

    fun fileFor(photo: PhotoEntity): File = repository.fileFor(photo)
    fun thumbFor(photo: PhotoEntity): File = repository.thumbFor(photo)
    fun newCameraTarget() = repository.newCameraTarget()

    fun importFromGallery(uris: List<Uri>, category: PhotoCategory) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy.update { true }
            uris.forEach { repository.importFromUri(it, jobId, category) }
            busy.update { false }
        }
    }

    fun importFromCapture(file: File, category: PhotoCategory) {
        viewModelScope.launch {
            busy.update { true }
            repository.importFromCapture(file, jobId, category)
            busy.update { false }
        }
    }

    fun setCaption(photo: PhotoEntity, caption: String) {
        viewModelScope.launch { repository.setCaption(photo, caption) }
    }

    fun setCategory(photo: PhotoEntity, category: PhotoCategory) {
        viewModelScope.launch { repository.setCategory(photo, category) }
    }

    fun delete(photo: PhotoEntity) {
        viewModelScope.launch { repository.delete(photo) }
    }
}

@Composable
fun PhotoTab(viewModel: PhotoTabViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The category chosen before shooting, so the photo is filed on the way in
    // rather than needing a tidy-up pass later.
    var pendingCategory by remember { mutableStateOf(PhotoCategory.PROGRESS) }
    var categoryMenu by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var captureFile by remember { mutableStateOf<File?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris -> viewModel.importFromGallery(uris, pendingCategory) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        captureFile?.let { file ->
            if (success) viewModel.importFromCapture(file, pendingCategory) else file.delete()
        }
        captureFile = null
    }

    fun launchCamera() {
        val (file, uri) = viewModel.newCameraTarget()
        captureFile = file
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) launchCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text("Camera", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                Text("Gallery", Modifier.padding(start = 8.dp))
            }
        }

        Box {
            TextButton(onClick = { categoryMenu = true }) {
                Text("Filing as: ${pendingCategory.label}")
            }
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                PhotoCategory.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { pendingCategory = option; categoryMenu = false }
                    )
                }
            }
        }

        if (state.busy) {
            Text(
                "Saving photos…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.photos.isEmpty()) {
            Spacer(Modifier.height(Dimens.cardGap))
            Card(Modifier.fillMaxWidth()) {
                Text(
                    "No photos yet. Shoot the before condition now — it is the record that " +
                        "settles arguments later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(14.dp)
                )
            }
            return@Column
        }

        if (state.usedCategories.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = { viewModel.setFilter(null) }) { Text("All") }
                state.usedCategories.forEach { category ->
                    TextButton(onClick = { viewModel.setFilter(category) }) {
                        Text(category.label)
                    }
                }
            }
        }

        SectionHeader("${state.visible.size} photos")

        // A fixed-height grid, because this sits inside the job detail's scrolling
        // column and a nested scroller would fight it.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().height(gridHeightFor(state.visible.size)),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            userScrollEnabled = false
        ) {
            items(state.visible, key = { it.id }) { photo ->
                val index = state.visible.indexOf(photo)
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clickableThumb { viewerIndex = index }
                ) {
                    AsyncImage(
                        model = viewModel.thumbFor(photo),
                        contentDescription = photo.caption ?: photo.category.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    viewerIndex?.let { start ->
        PhotoViewer(
            photos = state.visible,
            startIndex = start,
            fileFor = viewModel::fileFor,
            onCaption = viewModel::setCaption,
            onCategory = viewModel::setCategory,
            onDelete = {
                viewModel.delete(it)
                viewerIndex = null
            },
            onDismiss = { viewerIndex = null }
        )
    }
}

private fun gridHeightFor(count: Int): androidx.compose.ui.unit.Dp {
    val rows = ((count + 2) / 3).coerceAtLeast(1)
    return (rows * 124).dp
}

private fun Modifier.clickableThumb(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))

@Composable
private fun PhotoViewer(
    photos: List<PhotoEntity>,
    startIndex: Int,
    fileFor: (PhotoEntity) -> File,
    onCaption: (PhotoEntity, String) -> Unit,
    onCategory: (PhotoEntity, PhotoCategory) -> Unit,
    onDelete: (PhotoEntity) -> Unit,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, photos.lastIndex)
    ) { photos.size }
    var categoryMenu by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = fileFor(photos[page]),
                    contentDescription = photos[page].caption,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }

            val current = photos[pagerState.currentPage.coerceIn(0, photos.lastIndex)]

            IconButton(
                onClick = { onDelete(current) },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.White)
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { categoryMenu = true }) {
                            StatusChip(
                                label = current.category.label,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        DropdownMenu(
                            expanded = categoryMenu,
                            onDismissRequest = { categoryMenu = false }
                        ) {
                            PhotoCategory.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        onCategory(current, option)
                                        categoryMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        Dates.formatDateTime(current.capturedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                var caption by remember(current.id) { mutableStateOf(current.caption.orEmpty()) }
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = { onCaption(current, caption) }) { Text("Save") }
                    }
                )
            }
        }
    }
}

private fun Modifier.background(color: Color): Modifier =
    this.then(androidx.compose.foundation.background(color))
