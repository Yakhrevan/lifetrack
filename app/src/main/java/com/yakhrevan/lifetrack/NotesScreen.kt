package com.yakhrevan.lifetrack

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.yakhrevan.lifetrack.ui.theme.LifeTrackColors

// ==========================================
// 1. DESIGN TOKENS
// ==========================================

private val DarkBackground get() = LifeTrackColors.Background
private val CardBackground get() = LifeTrackColors.Surface
private val CardInner get() = LifeTrackColors.SurfaceRaised
private val CardBorder get() = LifeTrackColors.Border
private val PurpleBrand get() = LifeTrackColors.Primary
private val CyanBrand get() = LifeTrackColors.Blue
private val GreenBrand = Color(0xFF10B981)
private val TextWhite get() = LifeTrackColors.TextPrimary
private val TextMuted get() = LifeTrackColors.TextSecondary
private val RedAccent = Color(0xFFEF4444)

// ==========================================
// 2. MAIN NOTES SCREEN COMPOSABLE
// ==========================================

@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Screen views: main list, folder detail, editor, note detail, and folder/tag management.
    var currentView by remember { mutableStateOf("list") }
    var editorReturnView by remember { mutableStateOf("list") }
    var detailReturnView by remember { mutableStateOf("list") }
    var activeFolderFilter by remember { mutableStateOf<String?>(null) }
    var selectedNoteId by remember { mutableStateOf<String?>(null) }
    var noteToEdit by remember { mutableStateOf<NoteItem?>(null) }

    val notes = remember(refreshTrigger) {
        NoteRepository.getNotes(context, includeArchived = false)
    }

    BackHandler(enabled = currentView != "list") {
        when (currentView) {
            "edit" -> currentView = if (selectedNoteId != null) "details" else "list"
            "create", "details" -> currentView = "list"
            "folder_detail" -> currentView = "folders_tags"
            "folders_tags" -> currentView = "list"
        }
    }

    when (currentView) {
        "list" -> {
            NotesMainListView(
                notes = notes,
                initialFilter = activeFolderFilter ?: "All",
                onFilterChanged = { activeFolderFilter = if (it in listOf("All", "Pinned", "Recent")) null else it },
                onRefresh = { refreshTrigger++ },
                onCreateNote = {
                    noteToEdit = null
                    editorReturnView = "list"
                    currentView = "create"
                },
                onNoteClick = { note ->
                    selectedNoteId = note.id
                    detailReturnView = "list"
                    currentView = "details"
                },
                onOpenFoldersAndTags = {
                    currentView = "folders_tags"
                },
                modifier = modifier
            )
        }
        "folder_detail" -> {
            NotesMainListView(
                notes = notes,
                initialFilter = activeFolderFilter ?: "All",
                screenTitle = activeFolderFilter?.removePrefix("tag:") ?: "Folder",
                onBack = { currentView = "folders_tags" },
                onFilterChanged = {},
                onRefresh = { refreshTrigger++ },
                onCreateNote = {
                    noteToEdit = null
                    editorReturnView = "folder_detail"
                    currentView = "create"
                },
                onNoteClick = { note ->
                    selectedNoteId = note.id
                    detailReturnView = "folder_detail"
                    currentView = "details"
                },
                onOpenFoldersAndTags = { currentView = "folders_tags" },
                modifier = modifier
            )
        }
        "create", "edit" -> {
            CreateOrEditNoteView(
                initialNote = noteToEdit,
                onSave = { note ->
                    NoteRepository.saveNote(context, note)
                    refreshTrigger++
                    currentView = editorReturnView
                },
                onCancel = {
                    currentView = editorReturnView
                },
                modifier = modifier
            )
        }
        "details" -> {
            val note = remember(selectedNoteId, refreshTrigger) {
                selectedNoteId?.let { NoteRepository.getNoteById(context, it) }
            }
            if (note == null) {
                currentView = "list"
            } else {
                NoteDetailsView(
                    note = note,
                    onBack = { currentView = detailReturnView },
                    onEdit = {
                        noteToEdit = note
                        editorReturnView = "details"
                        currentView = "edit"
                    },
                    onRefresh = { refreshTrigger++ },
                    onDeleted = {
                        selectedNoteId = null
                        refreshTrigger++
                        currentView = detailReturnView
                    },
                    modifier = modifier
                )
            }
        }
        "folders_tags" -> {
            FoldersAndTagsView(
                onBack = {
                    activeFolderFilter = null
                    currentView = "list"
                },
                onRefresh = { refreshTrigger++ },
                onSelectFolder = { folderName ->
                    activeFolderFilter = folderName
                    currentView = "folder_detail"
                },
                refreshTrigger = refreshTrigger,
                modifier = modifier
            )
        }
    }
}

// ==========================================
// 3. NOTES MAIN LIST VIEW (SCREEN 8)
// ==========================================

@Composable
private fun NotesMainListView(
    notes: List<NoteItem>,
    initialFilter: String = "All",
    onFilterChanged: (String) -> Unit = {},
    onRefresh: () -> Unit,
    onCreateNote: () -> Unit,
    onNoteClick: (NoteItem) -> Unit,
    onOpenFoldersAndTags: () -> Unit,
    screenTitle: String = "Notes",
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(false) }

    val userFolders = remember(notes, initialFilter) { NoteRepository.getFolders(context) }

    val filteredNotes = remember(notes, selectedFilter, searchQuery) {
        notes.filter { note ->
            val matchesQuery = searchQuery.isBlank() ||
                    note.title.contains(searchQuery, ignoreCase = true) ||
                    note.content.contains(searchQuery, ignoreCase = true) ||
                    note.folder.contains(searchQuery, ignoreCase = true) ||
                    note.tags.any { it.contains(searchQuery, ignoreCase = true) }
            if (!matchesQuery) return@filter false

            when (selectedFilter) {
                "All" -> true
                "Pinned" -> note.isPinned
                "Recent" -> note.updatedAtMs >= System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                else -> if (selectedFilter.startsWith("tag:", ignoreCase = true)) {
                    val selectedTag = selectedFilter.removePrefix("tag:")
                    note.tags.any { it.equals(selectedTag, ignoreCase = true) }
                } else {
                    note.folder.equals(selectedFilter, ignoreCase = true)
                }
            }
        }.sortedWith(
            compareByDescending<NoteItem> { it.isPinned }
                .thenByDescending { it.updatedAtMs }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 110.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (onBack != null) {
                            IconButton(onClick = onBack, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back to folders", tint = TextWhite)
                            }
                        }
                        Text(screenTitle, color = TextWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (onBack != null && selectedFilter.startsWith("tag:", ignoreCase = true)) "Notes tagged with #${selectedFilter.removePrefix("tag:")}"
                        else if (onBack != null) "Notes in this folder"
                        else "Capture your ideas and thoughts",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenFoldersAndTags,
                        modifier = Modifier
                            .size(38.dp)
                            .background(CardInner, CircleShape)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Folders and Tags", tint = TextWhite, modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { showSearch = !showSearch },
                        modifier = Modifier
                            .size(38.dp)
                            .background(CardInner, CircleShape)
                    ) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search", tint = TextWhite, modifier = Modifier.size(18.dp))
                    }

                    Button(
                        onClick = onCreateNote,
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Note", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }

        // Expandable Search Bar
        if (showSearch) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search your notes...", color = TextMuted, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = PurpleBrand,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardInner,
                        unfocusedContainerColor = CardInner
                    ),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "Clear search", tint = TextMuted)
                            }
                        }
                    }
                )
            }
        }

        // File-manager style folder browser on the Notes home screen.
        if (onBack == null && selectedFilter == "All" && searchQuery.isBlank()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Folders", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${userFolders.size} folders · ${notes.count { it.folder.isBlank() }} unfiled notes", color = TextMuted, fontSize = 11.sp)
                    }
                    TextButton(onClick = onOpenFoldersAndTags) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = PurpleBrand, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Manage", color = PurpleBrand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (userFolders.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFoldersAndTags),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(44.dp).background(PurpleBrand.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.CreateNewFolder, null, tint = PurpleBrand, modifier = Modifier.size(23.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Create your first folder", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Organize notes like files and folders", color = TextMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
                        }
                    }
                }
            } else {
                items(userFolders.chunked(2)) { folderRow ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        folderRow.forEach { folder ->
                            val folderNotes = notes.filter { it.folder.equals(folder.name, ignoreCase = true) }
                            Card(
                                modifier = Modifier.weight(1f).clickable {
                                    selectedFilter = folder.name
                                    onFilterChanged(folder.name)
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(38.dp).background(PurpleBrand.copy(alpha = 0.16f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Folder, null, tint = PurpleBrand, modifier = Modifier.size(22.dp))
                                        }
                                        Text("${folderNotes.size}", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(folder.name, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (folderNotes.isEmpty()) "No notes yet" else "${folderNotes.size} files", color = TextMuted, fontSize = 10.sp)
                                }
                            }
                        }
                        if (folderRow.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // Filter Pills Row (hidden inside a folder/tag detail so its scope cannot be lost)
        if (onBack == null) item {
            val filterOptions = mutableListOf("All", "Pinned", "Recent")
            userFolders.forEach { filterOptions.add(it.name) }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterOptions) { filter ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) PurpleBrand else CardInner)
                            .clickable {
                                selectedFilter = filter
                                onFilterChanged(filter)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            filter,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // EMPTY STATE (Shown strictly when user has created 0 notes)
        if (notes.isEmpty() && onBack == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .background(PurpleBrand.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.MenuBook, null, tint = PurpleBrand, modifier = Modifier.size(38.dp))
                        }

                        Text("No notes yet", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                        Text(
                            "Write your first note to capture ideas, plans or important information.",
                            color = TextMuted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Button(
                            onClick = onCreateNote,
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Create Note", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (filteredNotes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(CardInner, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SearchOff, null, tint = TextMuted, modifier = Modifier.size(28.dp))
                        }
                        Text(
                            if (searchQuery.isNotBlank()) "No notes match \"$searchQuery\""
                            else "No notes in \"$selectedFilter\"",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "Try different keywords or switch folder filters to view other notes.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                searchQuery = ""
                                selectedFilter = "All"
                                onFilterChanged("All")
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PurpleBrand)
                        ) {
                            Text("Clear Search & Filters", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            // Notes List
            items(filteredNotes, key = { it.id }) { note ->
                NoteCardRow(
                    note = note,
                    onClick = { onNoteClick(note) }
                )
            }
        }
    }
}

// ==========================================
// 4. NOTE CARD ROW COMPONENT
// ==========================================

@Composable
private fun NoteCardRow(
    note: NoteItem,
    onClick: () -> Unit
) {
    val noteColor = HabitRoutineIconHelper.parseColor(note.colorHex)
    val timeFormatted = remember(note.updatedAtMs) {
        SimpleDateFormat("d MMM, hh:mm a", Locale.getDefault()).format(Date(note.updatedAtMs))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Folder icon box
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(noteColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, null, tint = noteColor, modifier = Modifier.size(18.dp))
                }

                Text(
                    note.title,
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (note.isPinned) {
                    Icon(Icons.Default.PushPin, "Pinned", tint = RedAccent, modifier = Modifier.size(16.dp))
                }
            }

            if (note.content.isNotBlank()) {
                Text(
                    note.content,
                    color = TextMuted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            // Checklist preview if any
            if (note.checklists.isNotEmpty()) {
                val doneCount = note.checklists.count { it.isChecked }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Checklist, null, tint = CyanBrand, modifier = Modifier.size(14.dp))
                    Text(
                        "$doneCount / ${note.checklists.size} checklist items",
                        color = CyanBrand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Footer: Folder badge / tags and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.folder.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .background(CardInner, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(note.folder, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    note.tags.take(2).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .background(PurpleBrand.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("#$tag", color = PurpleBrand, fontSize = 10.sp)
                        }
                    }
                }

                Text(timeFormatted, color = TextMuted, fontSize = 11.sp)
            }
        }
    }
}

// ==========================================
// 5. CREATE OR EDIT NOTE VIEW (SCREEN 9 & 10)
// ==========================================

@Composable
private fun CreateOrEditNoteView(
    initialNote: NoteItem?,
    onSave: (NoteItem) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isEdit = initialNote != null && initialNote.title.isNotBlank()

    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var selectedFolder by remember { mutableStateOf(initialNote?.folder ?: "") }
    var tagsInput by remember { mutableStateOf(initialNote?.tags?.joinToString(", ") ?: "") }
    var selectedColorHex by remember { mutableStateOf(initialNote?.colorHex ?: "#8B5CF6") }
    var isPinned by remember { mutableStateOf(initialNote?.isPinned ?: false) }
    var checklists by remember { mutableStateOf(initialNote?.checklists?.toMutableList() ?: mutableListOf<NoteChecklistItem>()) }
    var newChecklistText by remember { mutableStateOf("") }
    var showAddChecklistInput by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val userFoldersState = remember { mutableStateOf(NoteRepository.getFolders(context)) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    val hasChanges = remember(title, content, selectedFolder, tagsInput, selectedColorHex, isPinned, checklists) {
        title != (initialNote?.title ?: "") ||
                content != (initialNote?.content ?: "") ||
                selectedFolder != (initialNote?.folder ?: "") ||
                tagsInput != (initialNote?.tags?.joinToString(", ") ?: "") ||
                selectedColorHex != (initialNote?.colorHex ?: "#8B5CF6") ||
                isPinned != (initialNote?.isPinned ?: false) ||
                checklists.toList() != (initialNote?.checklists ?: emptyList<NoteChecklistItem>())
    }
    var showDiscardDialog by remember { mutableStateOf(false) }

    fun handleCancel() {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onCancel()
        }
    }

    BackHandler(enabled = true) {
        handleCancel()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard Unsaved Changes?", fontWeight = FontWeight.Bold, color = TextWhite) },
            text = { Text("You have unsaved edits in this note. Are you sure you want to discard them?", color = TextMuted) },
            containerColor = CardBackground,
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onCancel()
                }) {
                    Text("Discard", color = RedAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing", color = PurpleBrand, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { handleCancel() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text(
                        if (isEdit) "Edit Note" else "Create Note",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isPinned = !isPinned }) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = if (isPinned) "Unpin" else "Pin",
                            tint = if (isPinned) RedAccent else TextMuted
                        )
                    }

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                errorMessage = "Please enter a note title."
                                return@Button
                            }
                            val tagList = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                            val note = (initialNote ?: NoteItem(title = title)).copy(
                                title = title.trim(),
                                content = content.trim(),
                                folder = selectedFolder.trim(),
                                tags = tagList,
                                colorHex = selectedColorHex,
                                isPinned = isPinned,
                                checklists = checklists.toList()
                            )
                            onSave(note)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick Note Starters / Templates
        if (!isEdit) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TEMPLATES & QUICK STARTERS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val templates = listOf(
                            "Checklist" to "checklist",
                            "Meeting" to "## Meeting Notes\n**Date:** ${SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())}\n**Attendees:** \n\n### Agenda\n- Discussion point 1\n\n### Action Items\n- Task 1",
                            "Reflection" to "## Daily Reflection\n### Highlights:\n- \n\n### Challenges & Learnings:\n- \n\n### Focus for Tomorrow:\n- ",
                            "Idea" to "## Concept / Idea\n### Problem Statement:\n\n### Proposed Solution:\n- \n\n### Next Steps:\n- "
                        )
                        items(templates) { (tplName, tplData) ->
                            SuggestionChip(
                                onClick = {
                                    if (title.isBlank()) title = tplName
                                    if (tplName == "Checklist") {
                                        checklists = (checklists + listOf(
                                            NoteChecklistItem(text = "First task"),
                                            NoteChecklistItem(text = "Second task")
                                        )).toMutableList()
                                    } else {
                                        content = if (content.isBlank()) tplData else "$content\n\n$tplData"
                                    }
                                },
                                label = { Text(tplName, fontSize = 12.sp) },
                                icon = {
                                    Icon(
                                        if (tplName == "Checklist") Icons.Default.Checklist else Icons.Default.AutoAwesome,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = PurpleBrand
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Formatting Toolbar Row (B, I, U, Lists, Checkbox)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardInner),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { content += "\n**Bold Text**" }) {
                        Text("B", color = TextWhite, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    IconButton(onClick = { content += "\n*Italic Text*" }) {
                        Text("I", color = TextWhite, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 16.sp)
                    }
                    IconButton(onClick = { content += "\n### Heading" }) {
                        Text("H", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    IconButton(onClick = { content += "\n• Bullet item" }) {
                        Icon(Icons.Default.FormatListBulleted, null, tint = TextWhite, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { content += "\n1. Numbered item" }) {
                        Icon(Icons.Default.FormatListNumbered, null, tint = TextWhite, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { showAddChecklistInput = true }) {
                        Icon(Icons.Default.Checklist, null, tint = CyanBrand, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }

        // Title Input
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMessage = null },
                    label = { Text("Note Title *") },
                    isError = errorMessage != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = PurpleBrand,
                        unfocusedBorderColor = CardBorder,
                        focusedContainerColor = CardInner,
                        unfocusedContainerColor = CardInner,
                        errorBorderColor = RedAccent
                    ),
                    singleLine = true
                )
                if (errorMessage != null) {
                    Text(errorMessage!!, color = RedAccent, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }

        // Body Content Input
        item {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Start writing your note here...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = PurpleBrand,
                    unfocusedBorderColor = CardBorder,
                    focusedContainerColor = CardInner,
                    unfocusedContainerColor = CardInner
                )
            )
        }

        // Interactive Checklists Section
        if (checklists.isNotEmpty() || showAddChecklistInput) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("CHECKLIST ITEMS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                        checklists.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val mutable = checklists.toMutableList()
                                        mutable[idx] = item.copy(isChecked = !item.isChecked)
                                        checklists = mutable
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (item.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    null,
                                    tint = if (item.isChecked) CyanBrand else TextMuted
                                )
                                Text(
                                    item.text,
                                    color = if (item.isChecked) TextMuted else TextWhite,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    val mutable = checklists.toMutableList()
                                    mutable.removeAt(idx)
                                    checklists = mutable
                                }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        if (showAddChecklistInput) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newChecklistText,
                                    onValueChange = { newChecklistText = it },
                                    placeholder = { Text("Enter checklist item...", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                                Button(
                                    onClick = {
                                        if (newChecklistText.isNotBlank()) {
                                            checklists = (checklists + NoteChecklistItem(text = newChecklistText.trim())).toMutableList()
                                            newChecklistText = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanBrand),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Add", color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Folder & Tags Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("FOLDER & TAGS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { newFolderName = ""; showNewFolderDialog = true }) {
                        Icon(Icons.Default.Add, null, tint = PurpleBrand, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("+ New Folder", color = PurpleBrand, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                val currentFolders = userFoldersState.value
                if (currentFolders.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(currentFolders) { folder ->
                            val isSelected = selectedFolder.equals(folder.name, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedFolder = if (isSelected) "" else folder.name
                                },
                                label = { Text(folder.name, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Folder, null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (selectedFolder.isBlank()) "No folder selected" else "Folder: $selectedFolder",
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (selectedFolder.isNotBlank()) {
                                TextButton(onClick = { selectedFolder = "" }) {
                                    Text("Remove", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                        if (currentFolders.isEmpty()) {
                            Text("Create a folder to organize this note.", color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("TAGS", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val availableTags = NoteRepository.getTags(context)
                        if (availableTags.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(availableTags) { tag ->
                                    val selected = tagsInput.split(",").map { it.trim() }.any { it.equals(tag.name, ignoreCase = true) }
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            val current = tagsInput.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                                            if (selected) current.removeAll { it.equals(tag.name, ignoreCase = true) }
                                            else current.add(tag.name)
                                            tagsInput = current.distinctBy { it.lowercase(Locale.ROOT) }.joinToString(", ")
                                        },
                                        label = { Text(tag.name, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = tagsInput,
                            onValueChange = { tagsInput = it },
                            label = { Text("Add tags (comma separated)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = PurpleBrand,
                                unfocusedBorderColor = CardBorder,
                                focusedContainerColor = CardInner,
                                unfocusedContainerColor = CardInner
                            ),
                            singleLine = true
                        )
                    }
                }

                // Color Palette
                Text("COLOR THEME", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HabitRoutineIconHelper.availableColors) { color ->
                        val hex = HabitRoutineIconHelper.colorToHex(color)
                        val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(color, CircleShape)
                                .border(if (isSelected) 3.dp else 0.dp, Color.White, CircleShape)
                                .clickable { selectedColorHex = hex }
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            item {
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }

    if (showNewFolderDialog) {
        var folderError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Create Folder", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it; folderError = null },
                        label = { Text("Folder Name") },
                        singleLine = true
                    )
                    if (folderError != null) Text(folderError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isBlank()) {
                            folderError = "Enter folder name"
                            return@Button
                        }
                        NoteRepository.saveFolder(context, NoteFolder(name = newFolderName.trim()))
                        userFoldersState.value = NoteRepository.getFolders(context)
                        selectedFolder = newFolderName.trim()
                        showNewFolderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBackground
        )
    }
}

// ==========================================
// 6. NOTE DETAILS VIEW (SCREEN 11)
// ==========================================

@Composable
private fun NoteDetailsView(
    note: NoteItem,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(note.isPinned) }
    val timeFormatted = remember(note.updatedAtMs) {
        SimpleDateFormat("d MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date(note.updatedAtMs))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
    ) {
        // Top Action Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = TextWhite)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        isPinned = NoteRepository.togglePin(context, note.id)
                        onRefresh()
                    }) {
                        Icon(Icons.Default.PushPin, null, tint = if (isPinned) RedAccent else TextMuted)
                    }

                    // Share Note
                    IconButton(onClick = {
                        val shareText = "${note.title}\n\n${note.content}"
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Note"))
                    }) {
                        Icon(Icons.Default.Share, null, tint = TextWhite)
                    }

                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Title and Metadata Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(note.title, color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(timeFormatted, color = TextMuted, fontSize = 12.sp)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (note.folder.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .background(PurpleBrand.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(note.folder, color = PurpleBrand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        note.tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .background(CyanBrand.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text("#$tag", color = CyanBrand, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Note Body Card
        if (note.content.isNotBlank()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Text(
                        note.content,
                        color = TextWhite,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            }
        }

        // Checklists if any
        if (note.checklists.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Checklist", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)

                        note.checklists.forEachIndexed { idx, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val updatedList = note.checklists.toMutableList()
                                        updatedList[idx] = item.copy(isChecked = !item.isChecked)
                                        NoteRepository.saveNote(context, note.copy(checklists = updatedList))
                                        onRefresh()
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    if (item.isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    null,
                                    tint = if (item.isChecked) CyanBrand else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    item.text,
                                    color = if (item.isChecked) TextMuted else TextWhite,
                                    fontSize = 14.sp,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action Menu
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column {
                    NoteActionRow(Icons.Default.Edit, "Edit Note", onClick = onEdit)
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    NoteActionRow(Icons.Default.ContentCopy, "Duplicate Note", onClick = {
                        NoteRepository.duplicateNote(context, note.id)
                        onRefresh()
                    })
                    HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                    NoteActionRow(
                        Icons.Default.Delete,
                        "Delete Note",
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = { showDeleteConfirm = true }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Note?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${note.title}'?", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        NoteRepository.deleteNote(context, note.id)
                        showDeleteConfirm = false
                        onDeleted()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBackground
        )
    }
}

@Composable
private fun NoteActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    textColor: Color = TextWhite,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = textColor, modifier = Modifier.size(20.dp))
        Text(title, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(16.dp))
    }
}

// ==========================================
// 7. FOLDERS & TAGS VIEW (SCREEN 12)
// ==========================================

@Composable
private fun FoldersAndTagsView(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFolder: (String) -> Unit = {},
    refreshTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf("Folders") } // "Folders" or "Tags"
    var showCreateDialog by remember { mutableStateOf(false) }
    var folderToDelete by remember { mutableStateOf<NoteFolder?>(null) }
    var tagToDelete by remember { mutableStateOf<NoteTag?>(null) }
    var newItemName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var localTrigger by remember { mutableIntStateOf(0) }

    val folders = remember(selectedTab, refreshTrigger, localTrigger) { NoteRepository.getFolders(context) }
    val tags = remember(selectedTab, refreshTrigger, localTrigger) { NoteRepository.getTags(context) }
    val allNotes = remember(refreshTrigger, localTrigger) { NoteRepository.getNotes(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 90.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = TextWhite)
                }
                Text("Folders & Tags", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Tabs: Folders | Tags
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("Folders", "Tags").forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) PurpleBrand else CardInner)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tab,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Add Item Button
        item {
            Button(
                onClick = {
                    newItemName = ""
                    error = null
                    showCreateDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add ${if (selectedTab == "Folders") "Folder" else "Tag"}", fontWeight = FontWeight.Bold)
            }
        }

        if (selectedTab == "Folders") {
            if (folders.isEmpty()) {
                item {
                    Text(
                        "No folders created yet. Create a folder to organize your notes.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(folders, key = { it.id }) { folder ->
                    val count = allNotes.count { it.folder.equals(folder.name, ignoreCase = true) }
                    val folderColor = HabitRoutineIconHelper.parseColor(folder.colorHex)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectFolder(folder.name) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 76.dp)
                                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(folderColor.copy(alpha = 0.16f), RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = "Folder ${folder.name}", tint = folderColor, modifier = Modifier.size(22.dp))
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(folder.name, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("$count ${if (count == 1) "note" else "notes"}", color = TextMuted, fontSize = 11.sp)
                            }

                            IconButton(onClick = { folderToDelete = folder }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete ${folder.name}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // Tags List
            if (tags.isEmpty()) {
                item {
                    Text(
                        "No tags created yet. Create custom tags to categorize notes across topics.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                items(tags, key = { it.id }) { tag ->
                    val count = allNotes.count { it.tags.any { t -> t.equals(tag.name, ignoreCase = true) } }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectFolder("tag:${tag.name}") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("#${tag.name}", color = CyanBrand, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("$count notes", color = TextMuted, fontSize = 11.sp)
                            IconButton(onClick = { tagToDelete = tag }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (folderToDelete != null) {
        val folder = folderToDelete!!
        val count = allNotes.count { it.folder.equals(folder.name, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text("Delete folder?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (count > 0) "This folder contains $count notes. Deleting it will move those notes to No folder; their content will be kept."
                    else "This folder is empty. Delete it?",
                    color = TextMuted
                )
            },
            containerColor = CardBackground,
            confirmButton = {
                TextButton(onClick = {
                    NoteRepository.deleteFolder(context, folder.id)
                    folderToDelete = null
                    localTrigger++
                    onRefresh()
                }) { Text("Delete folder", color = RedAccent) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    if (tagToDelete != null) {
        val tag = tagToDelete!!
        val count = allNotes.count { note -> note.tags.any { it.equals(tag.name, ignoreCase = true) } }
        AlertDialog(
            onDismissRequest = { tagToDelete = null },
            title = { Text("Delete tag?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (count > 0) "This tag is used by $count notes. Deleting it will remove this tag from those notes but keep their content and other tags."
                    else "This tag is not used by any notes. Delete it?",
                    color = TextMuted
                )
            },
            containerColor = CardBackground,
            confirmButton = {
                TextButton(onClick = {
                    NoteRepository.deleteTag(context, tag.id)
                    tagToDelete = null
                    localTrigger++
                    onRefresh()
                }) { Text("Delete tag", color = RedAccent) }
            },
            dismissButton = {
                TextButton(onClick = { tagToDelete = null }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    if (showCreateDialog) {
        val isFolder = selectedTab == "Folders"
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create ${if (isFolder) "Folder" else "Tag"}", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it; error = null },
                        label = { Text("${if (isFolder) "Folder" else "Tag"} Name") },
                        singleLine = true
                    )
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newItemName.isBlank()) {
                            error = "Enter a name."
                            return@Button
                        }
                        if (isFolder) {
                            NoteRepository.saveFolder(context, NoteFolder(name = newItemName.trim()))
                        } else {
                            NoteRepository.saveTag(context, NoteTag(name = newItemName.trim()))
                        }
                        localTrigger++
                        showCreateDialog = false
                        onRefresh()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleBrand)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = TextMuted) }
            },
            containerColor = CardBackground
        )
    }
}
