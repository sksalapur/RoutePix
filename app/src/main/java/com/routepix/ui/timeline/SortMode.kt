package com.routepix.ui.timeline


sealed class SortMode(val label: String) {
    data object ByDate : SortMode("Date")
    data object ByUploader : SortMode("Uploaded by")
    data object ByTag : SortMode("Tag")
}

