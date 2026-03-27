package com.routepix.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class SavedPhotosViewModel : ViewModel() {

    private val _savedFiles = MutableStateFlow<List<File>>(emptyList())
    val savedFiles = _savedFiles.asStateFlow()

    fun loadSavedPhotos(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedDir = File(context.filesDir, "saved")
            if (savedDir.exists()) {
                val files = savedDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()
                _savedFiles.value = files
            } else {
                _savedFiles.value = emptyList()
            }
        }
    }

    fun deletePhoto(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            if (file.exists()) {
                file.delete()
                val currentList = _savedFiles.value.toMutableList()
                currentList.remove(file)
                _savedFiles.value = currentList
            }
        }
    }
}
