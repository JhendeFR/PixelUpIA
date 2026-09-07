package com.jhendefr.pixelupia.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SmartGalleryPreferencesDataSource(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("snapvault_smart_gallery_prefs", Context.MODE_PRIVATE)

    private val keyHiddenCollections = "hidden_smart_collection_ids"
    private val keyChosenFolders = "chosen_index_folder_names"
    private val keyShowIndexedCount = "show_indexed_count_toggle"

    private val _hiddenCollectionsFlow = MutableStateFlow(loadHiddenCollections())
    val hiddenCollectionsFlow: Flow<Set<String>> = _hiddenCollectionsFlow.asStateFlow()

    private val _chosenFoldersFlow = MutableStateFlow(loadChosenFolders())
    val chosenFoldersFlow: Flow<Set<String>> = _chosenFoldersFlow.asStateFlow()

    private val _showIndexedCountFlow = MutableStateFlow(loadShowIndexedCount())
    val showIndexedCountFlow: Flow<Boolean> = _showIndexedCountFlow.asStateFlow()

    private fun loadHiddenCollections(): Set<String> {
        return prefs.getStringSet(keyHiddenCollections, emptySet()) ?: emptySet()
    }

    private fun loadChosenFolders(): Set<String> {
        val stored = prefs.getStringSet(keyChosenFolders, null)
        return if (stored.isNullOrEmpty()) {
            setOf("Screenshots")
        } else {
            stored
        }
    }

    private fun loadShowIndexedCount(): Boolean {
        return prefs.getBoolean(keyShowIndexedCount, true)
    }

    fun hideCollection(collectionId: String) {
        val current = loadHiddenCollections().toMutableSet()
        current.add(collectionId)
        prefs.edit().putStringSet(keyHiddenCollections, current).apply()
        _hiddenCollectionsFlow.value = current
    }

    fun unhideCollection(collectionId: String) {
        val current = loadHiddenCollections().toMutableSet()
        current.remove(collectionId)
        prefs.edit().putStringSet(keyHiddenCollections, current).apply()
        _hiddenCollectionsFlow.value = current
    }

    fun isCollectionHidden(collectionId: String): Boolean {
        return loadHiddenCollections().contains(collectionId)
    }

    fun setChosenFolders(folders: Set<String>) {
        val finalFolders = if (folders.isEmpty()) setOf("Screenshots") else folders
        prefs.edit().putStringSet(keyChosenFolders, finalFolders).apply()
        _chosenFoldersFlow.value = finalFolders
    }

    fun toggleFolderIndexing(folderName: String) {
        val current = loadChosenFolders().toMutableSet()
        if (current.contains(folderName)) {
            current.remove(folderName)
        } else {
            current.add(folderName)
        }
        val finalFolders = if (current.isEmpty()) setOf("Screenshots") else current
        prefs.edit().putStringSet(keyChosenFolders, finalFolders).apply()
        _chosenFoldersFlow.value = finalFolders
    }

    fun toggleShowIndexedCount(): Boolean {
        val current = loadShowIndexedCount()
        val newValue = !current
        prefs.edit().putBoolean(keyShowIndexedCount, newValue).apply()
        _showIndexedCountFlow.value = newValue
        return newValue
    }
}
