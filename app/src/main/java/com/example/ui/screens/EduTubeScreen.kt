package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.SavedVideo
import com.example.data.remote.EduTubeSearchResult
import com.example.data.remote.EduTubeService
import com.example.data.remote.EduVideoItem
import com.example.ui.components.VidhyaIcons
import com.example.viewmodel.UiState

@Composable
fun EduTubeScreen(
    searchQuery: String,
    selectedCategory: String,
    searchResult: EduTubeSearchResult,
    isLoading: Boolean,
    savedVideosState: UiState<List<SavedVideo>>,
    onSearch: (query: String, category: String) -> Unit,
    onSaveOrRecordVideo: (EduVideoItem, Boolean) -> Unit
) {
    val context = LocalContext.current
    var queryInput by rememberSaveable(searchQuery) { mutableStateOf(searchQuery) }
    var activeTab by rememberSaveable { mutableStateOf("DISCOVER") } // DISCOVER, SAVED
    var selectedVideoForDetails by remember { mutableStateOf<EduVideoItem?>(null) }

    val savedVideos = (savedVideosState as? UiState.Success)?.data.orEmpty()

    fun openVideoInSupportedPlayer(videoId: String) {
        try {
            val ytUri = Uri.parse("https://www.youtube.com/watch?v=$videoId")
            val intent = Intent(Intent.ACTION_VIEW, ytUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Ignore if no browser/player installed
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EduTube Learning",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Focused educational video discovery with entertainment filtering",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = activeTab == "DISCOVER",
                        onClick = { activeTab = "DISCOVER" },
                        label = { Text("Discover") }
                    )
                    FilterChip(
                        selected = activeTab == "SAVED",
                        onClick = { activeTab = "SAVED" },
                        label = { Text("Library (${savedVideos.size})") }
                    )
                }
            }
        }

        if (activeTab == "DISCOVER") {
            // Education-Focused Search Bar
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = { queryInput = it },
                        placeholder = { Text("Search calculus, physics, history...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(imageVector = VidhyaIcons.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (queryInput.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        queryInput = ""
                                        onSearch("", selectedCategory)
                                    }
                                ) {
                                    Icon(imageVector = VidhyaIcons.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { onSearch(queryInput, selectedCategory) }
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("edutube_search_input")
                    )

                    Button(
                        onClick = { onSearch(queryInput, selectedCategory) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("edutube_search_button")
                    ) {
                        Text("Search")
                    }
                }
            }

            // Subject Categories Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EduTubeService.SUBJECT_CATEGORIES.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { onSearch(queryInput, category) },
                            label = { Text(category) }
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                when (searchResult) {
                    is EduTubeSearchResult.BlockedEntertainment -> {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edutube_blocked_banner")
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = VidhyaIcons.BlockedApps,
                                        contentDescription = "Blocked entertainment query",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = searchResult.reasonMessage,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Query '${searchResult.matchedTerm}' matched EduTube's entertainment filter. Try searching for a syllabus subject, lecture, or academic concept instead.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    is EduTubeSearchResult.Error -> {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text(
                                        text = "Search Unavailable",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = searchResult.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(onClick = { onSearch(queryInput, selectedCategory) }) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }

                    is EduTubeSearchResult.Success -> {
                        if (!searchResult.fromLiveApi) {
                            item {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Showing Curated Educational Catalog (Demonstration Content). Add YOUTUBE_API_KEY in AI Studio Secrets for live YouTube Data API v3 search.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }

                        if (searchResult.videos.isEmpty()) {
                            item {
                                Text(
                                    text = "No matching educational lessons found. Try another academic topic or subject category.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 20.dp)
                                )
                            }
                        } else {
                            items(searchResult.videos, key = { it.videoId }) { video ->
                                EduVideoCard(
                                    video = video,
                                    isSaved = savedVideos.any { it.youtubeVideoId == video.videoId && it.isSaved },
                                    onWatchClick = {
                                        onSaveOrRecordVideo(video, false)
                                        selectedVideoForDetails = video
                                    },
                                    onBookmarkClick = {
                                        onSaveOrRecordVideo(video, true)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Saved & Recently Watched Library
            if (savedVideos.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.BookmarkOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No saved or recently watched lessons",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Bookmark lectures in Discover to build your revision playlist.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(savedVideos, key = { it.id }) { saved ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { openVideoInSupportedPlayer(saved.youtubeVideoId) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                imageVector = VidhyaIcons.EduTubeFilled,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = saved.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2
                                )
                                Text(
                                    text = "${saved.channelTitle} • ${saved.subjectCategory}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedVideoForDetails != null) {
        val v = selectedVideoForDetails!!
        AlertDialog(
            onDismissRequest = { selectedVideoForDetails = null },
            title = { Text(v.title, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${v.channelTitle} • ${v.subjectCategory} • ${v.durationText}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = v.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                    HorizontalDivider()
                    Text(
                        text = "Classification: ${v.educationalReason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = v.videoId
                        selectedVideoForDetails = null
                        openVideoInSupportedPlayer(id)
                    }
                ) {
                    Icon(imageVector = VidhyaIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Watch in YouTube Player")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedVideoForDetails = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun EduVideoCard(
    video: EduVideoItem,
    isSaved: Boolean,
    onWatchClick: () -> Unit,
    onBookmarkClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onWatchClick)
            .testTag("edutube_video_${video.videoId}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = video.durationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = video.subjectCategory.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = video.channelTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onBookmarkClick) {
                        Icon(
                            imageVector = if (isSaved) VidhyaIcons.BookmarkFilled else VidhyaIcons.BookmarkOutline,
                            contentDescription = "Save lesson",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
