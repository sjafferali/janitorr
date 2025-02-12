package com.github.schaka.janitorr.jellyfin.library

enum class LibraryType(
        val collectionType: String,
        val collectionName: String,
        val folderName: String)
{

    MOVIES("Movies", "Movies", "movies"),
    TV_SHOWS("TvShows", "Shows", "tv")

}