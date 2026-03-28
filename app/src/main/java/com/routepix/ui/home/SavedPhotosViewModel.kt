package com.routepix.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class SavedPhoto(
    val file: File,
    val tag: String
)

class SavedPhotosViewModel : ViewModel() {

    private val _savedPhotos = MutableStateFlow<List<SavedPhoto>>(emptyList())
    val savedPhotos = _savedPhotos.asStateFlow()

    private val _savedFiles = MutableStateFlow<List<File>>(emptyList())
    val savedFiles = _savedFiles.asStateFlow()

    fun loadSavedPhotos(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedDir = File(context.filesDir, "saved")
            if (!savedDir.exists()) {
                _savedPhotos.value = emptyList()
                _savedFiles.value = emptyList()
                return@launch
            }

            val photos = mutableListOf<SavedPhoto>()
            
            // Load from tag subdirectories
            savedDir.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    child.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }
                        ?.forEach { file ->
                            photos.add(SavedPhoto(file, child.name))
                        }
                } else if (child.isFile && child.name.endsWith(".jpg")) {
                    // Legacy flat files
                    photos.add(SavedPhoto(child, "Uncategorized"))
                }
            }

            photos.sortByDescending { it.file.lastModified() }
            _savedPhotos.value = photos
            _savedFiles.value = photos.map { it.file }
        }
    }

    fun deletePhoto(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists()) {
                file.delete()
                _savedPhotos.value = _savedPhotos.value.filter { it.file != file }
                _savedFiles.value = _savedFiles.value.filter { it != file }
            }
        }
    }

    fun deletePhotos(files: Set<File>) {
        viewModelScope.launch(Dispatchers.IO) {
            files.forEach { it.delete() }
            _savedPhotos.value = _savedPhotos.value.filter { it.file !in files }
            _savedFiles.value = _savedFiles.value.filter { it !in files }
        }
    }
}
