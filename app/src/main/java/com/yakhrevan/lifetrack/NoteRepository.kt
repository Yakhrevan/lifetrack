package com.yakhrevan.lifetrack

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ==========================================
// 1. DATA MODELS
// ==========================================

data class NoteChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false
)

data class NoteItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String = "",
    val folder: String = "",
    val tags: List<String> = emptyList(),
    val colorHex: String = "#8B5CF6", // Purple
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val reminderTime: String? = null,
    val checklists: List<NoteChecklistItem> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class NoteFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#F59E0B"
)

data class NoteTag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: String = "#38BDF8"
)

// ==========================================
// 2. REPOSITORY & ENGINE
// ==========================================

object NoteRepository {
    private const val PREFS_NAME = "lifetrack_notes_v2"
    private const val KEY_NOTES = "notes_v2"
    private const val KEY_FOLDERS = "note_folders_v2"
    private const val KEY_TAGS = "note_tags_v2"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Strictly returns only saved user notes. Never seeds default content.
     */
    fun getNotes(context: Context, includeArchived: Boolean = false): List<NoteItem> {
        val raw = prefs(context).getString(KEY_NOTES, null)
        val notes = if (raw.isNullOrBlank()) emptyList() else decodeNotes(raw)
        return if (includeArchived) notes else notes.filterNot { it.isArchived }
    }

    fun getNoteById(context: Context, id: String): NoteItem? {
        return getNotes(context, includeArchived = true).find { it.id == id }
    }

    fun saveNote(context: Context, note: NoteItem) {
        val current = getNotes(context, includeArchived = true).toMutableList()
        val index = current.indexOfFirst { it.id == note.id }
        val updatedNote = note.copy(updatedAtMs = System.currentTimeMillis())
        if (index >= 0) {
            current[index] = updatedNote
        } else {
            current.add(0, updatedNote) // Newest first
        }
        prefs(context).edit().putString(KEY_NOTES, encodeNotes(current)).apply()
    }

    fun togglePin(context: Context, id: String): Boolean {
        val note = getNoteById(context, id) ?: return false
        val updated = note.copy(isPinned = !note.isPinned)
        saveNote(context, updated)
        return updated.isPinned
    }

    fun duplicateNote(context: Context, id: String): NoteItem? {
        val existing = getNoteById(context, id) ?: return null
        val duplicated = existing.copy(
            id = UUID.randomUUID().toString(),
            title = "${existing.title} (Copy)",
            isPinned = false,
            createdAtMs = System.currentTimeMillis(),
            updatedAtMs = System.currentTimeMillis()
        )
        saveNote(context, duplicated)
        return duplicated
    }

    fun toggleArchiveNote(context: Context, id: String): Boolean {
        val note = getNoteById(context, id) ?: return false
        val updated = note.copy(isArchived = !note.isArchived)
        saveNote(context, updated)
        return updated.isArchived
    }

    fun deleteNote(context: Context, id: String) {
        val current = getNotes(context, includeArchived = true).filterNot { it.id == id }
        prefs(context).edit().putString(KEY_NOTES, encodeNotes(current)).apply()
    }

    // ------------------------------------------
    // FILTERED QUERIES
    // ------------------------------------------

    fun searchNotes(context: Context, query: String): List<NoteItem> {
        if (query.isBlank()) return getNotes(context)
        return getNotes(context).filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true) ||
                    it.tags.any { tag -> tag.contains(query, ignoreCase = true) } ||
                    it.folder.contains(query, ignoreCase = true)
        }
    }

    fun getPinnedNotes(context: Context): List<NoteItem> {
        return getNotes(context).filter { it.isPinned }
    }

    fun getRecentNotes(context: Context, limit: Int = 5): List<NoteItem> {
        return getNotes(context).sortedByDescending { it.updatedAtMs }.take(limit)
    }

    fun getNotesByFolder(context: Context, folderName: String): List<NoteItem> {
        return getNotes(context).filter { it.folder.equals(folderName, ignoreCase = true) }
    }

    // ------------------------------------------
    // USER FOLDERS & TAGS
    // ------------------------------------------

    fun getFolders(context: Context): List<NoteFolder> {
        val raw = prefs(context).getString(KEY_FOLDERS, null)
        return if (raw.isNullOrBlank()) emptyList() else decodeFolders(raw)
    }

    fun saveFolder(context: Context, folder: NoteFolder) {
        val current = getFolders(context).toMutableList()
        val index = current.indexOfFirst { it.id == folder.id || it.name.equals(folder.name, ignoreCase = true) }
        if (index >= 0) {
            current[index] = folder
        } else {
            current.add(folder)
        }
        prefs(context).edit().putString(KEY_FOLDERS, encodeFolders(current)).apply()
    }

    fun deleteFolder(context: Context, id: String) {
        val folder = getFolders(context).find { it.id == id }
        val current = getFolders(context).filterNot { it.id == id }
        prefs(context).edit().putString(KEY_FOLDERS, encodeFolders(current)).apply()
        // Clear folder association on notes if folder deleted
        if (folder != null) {
            val notes = getNotes(context, includeArchived = true)
            notes.filter { it.folder.equals(folder.name, ignoreCase = true) }.forEach {
                saveNote(context, it.copy(folder = ""))
            }
        }
    }

    fun getTags(context: Context): List<NoteTag> {
        val raw = prefs(context).getString(KEY_TAGS, null)
        return if (raw.isNullOrBlank()) emptyList() else decodeTags(raw)
    }

    fun saveTag(context: Context, tag: NoteTag) {
        val current = getTags(context).toMutableList()
        val index = current.indexOfFirst { it.id == tag.id || it.name.equals(tag.name, ignoreCase = true) }
        if (index >= 0) {
            current[index] = tag
        } else {
            current.add(tag)
        }
        prefs(context).edit().putString(KEY_TAGS, encodeTags(current)).apply()
    }

    fun deleteTag(context: Context, id: String) {
        val tag = getTags(context).find { it.id == id } ?: return
        val current = getTags(context).filterNot { it.id == id }
        prefs(context).edit().putString(KEY_TAGS, encodeTags(current)).apply()

        // Remove the deleted tag from saved notes without touching note content or other tags.
        getNotes(context, includeArchived = true)
            .filter { note -> note.tags.any { it.equals(tag.name, ignoreCase = true) } }
            .forEach { note ->
                saveNote(
                    context,
                    note.copy(tags = note.tags.filterNot { it.equals(tag.name, ignoreCase = true) })
                )
            }
    }

    // ------------------------------------------
    // JSON SERIALIZATION
    // ------------------------------------------

    private fun encodeNotes(notes: List<NoteItem>): String {
        val arr = JSONArray()
        for (n in notes) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("title", n.title)
            obj.put("content", n.content)
            obj.put("folder", n.folder)
            obj.put("tags", JSONArray(n.tags))
            obj.put("colorHex", n.colorHex)
            obj.put("isPinned", n.isPinned)
            obj.put("isArchived", n.isArchived)
            if (n.reminderTime != null) obj.put("reminderTime", n.reminderTime)
            obj.put("createdAtMs", n.createdAtMs)
            obj.put("updatedAtMs", n.updatedAtMs)

            val checkArr = JSONArray()
            for (chk in n.checklists) {
                val cObj = JSONObject()
                cObj.put("id", chk.id)
                cObj.put("text", chk.text)
                cObj.put("isChecked", chk.isChecked)
                checkArr.put(cObj)
            }
            obj.put("checklists", checkArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeNotes(raw: String): List<NoteItem> = runCatching {
        val arr = JSONArray(raw)
        val list = mutableListOf<NoteItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val tagsArr = obj.optJSONArray("tags")
            val tags = if (tagsArr != null) (0 until tagsArr.length()).map { tagsArr.optString(it) } else emptyList()

            val checkArr = obj.optJSONArray("checklists")
            val checklists = mutableListOf<NoteChecklistItem>()
            if (checkArr != null) {
                for (j in 0 until checkArr.length()) {
                    val cObj = checkArr.optJSONObject(j) ?: continue
                    checklists.add(
                        NoteChecklistItem(
                            id = cObj.optString("id", UUID.randomUUID().toString()),
                            text = cObj.optString("text", ""),
                            isChecked = cObj.optBoolean("isChecked", false)
                        )
                    )
                }
            }

            list.add(
                NoteItem(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    title = obj.optString("title", "Untitled Note"),
                    content = obj.optString("content", ""),
                    folder = obj.optString("folder", ""),
                    tags = tags,
                    colorHex = obj.optString("colorHex", "#8B5CF6"),
                    isPinned = obj.optBoolean("isPinned", false),
                    isArchived = obj.optBoolean("isArchived", false),
                    reminderTime = if (obj.has("reminderTime")) obj.optString("reminderTime") else null,
                    checklists = checklists,
                    createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
                    updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis())
                )
            )
        }
        list
    }.getOrDefault(emptyList())

    private fun encodeFolders(folders: List<NoteFolder>): String {
        val arr = JSONArray()
        for (f in folders) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("name", f.name)
            obj.put("colorHex", f.colorHex)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeFolders(raw: String): List<NoteFolder> = runCatching {
        val arr = JSONArray(raw)
        val list = mutableListOf<NoteFolder>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                NoteFolder(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Folder"),
                    colorHex = obj.optString("colorHex", "#F59E0B")
                )
            )
        }
        list
    }.getOrDefault(emptyList())

    private fun encodeTags(tags: List<NoteTag>): String {
        val arr = JSONArray()
        for (t in tags) {
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("name", t.name)
            obj.put("colorHex", t.colorHex)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeTags(raw: String): List<NoteTag> = runCatching {
        val arr = JSONArray(raw)
        val list = mutableListOf<NoteTag>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                NoteTag(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Tag"),
                    colorHex = obj.optString("colorHex", "#38BDF8")
                )
            )
        }
        list
    }.getOrDefault(emptyList())
}
