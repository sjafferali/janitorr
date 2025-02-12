package com.github.schaka.janitorr.servarr.radarr

import com.github.schaka.janitorr.ApplicationProperties
import com.github.schaka.janitorr.FileSystemProperties
import com.github.schaka.janitorr.servarr.LibraryItem
import com.github.schaka.janitorr.servarr.ServarrService
import com.github.schaka.janitorr.servarr.sonarr.SonarrService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.io.path.exists

@Service
class RadarrService(

        val radarrClient: RadarrClient,

        val applicationProperties: ApplicationProperties,

        val fileSystemProperties: FileSystemProperties,

        var upgradesAllowed: Boolean = false

) : ServarrService {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @PostConstruct
    fun postConstruct() {
        upgradesAllowed = radarrClient.getAllQualityProfiles().any { it.items.isNotEmpty() && it.upgradeAllowed }
    }

    override fun getEntries(): List<LibraryItem> {
        return radarrClient.getAllMovies().mapNotNull { movie ->
            radarrClient.getHistory(movie.id)
                    .filter { it.eventType == "downloadFolderImported" && it.data.droppedPath != null }
                    .map {
                        LibraryItem(
                                movie.id,
                                LocalDateTime.parse(it.date.substring(0, it.date.length - 1)),
                                it.data.droppedPath!!,
                                it.data.importedPath!!,
                                movie.path,
                                movie.rootFolderPath!!,
                                movie.movieFile!!.path,
                                tmdbId = movie.tmdbId,
                                imdbId = movie.imdbId
                        )
                    }
                    .sortedWith ( byDate(upgradesAllowed) )
                    .firstOrNull()
        }
    }

    override fun removeEntries(items: List<LibraryItem>) {
        for (movie in items) {

            if (fileSystemProperties.access && Path.of(movie.originalPath).exists()) {
                log.info("Can't delete movie [still seeding - file exists] ({}), id: {}, imdb: {}", movie.originalPath, movie.id, movie.imdbId)
                movie.seeding = true
                continue
            }

            if (!applicationProperties.dryRun) {
                unmonitorMovie(movie.id)
                radarrClient.deleteMovie(movie.id)
                log.info("Deleting movie ({}), id: {}, imdb: {}", movie.parentPath, movie.id, movie.imdbId)
            } else {
                log.info("Deleting movie ({}), id: {}, imdb: {}", movie.parentPath, movie.id, movie.imdbId)
            }
        }
    }

    private fun unmonitorMovie(movieId: Int) {
        val movie = radarrClient.getMovie(movieId)
        val isMonitored = movie.monitored
        movie.monitored = false
        radarrClient.updateMovie(movieId, movie)

        if (isMonitored) {
            log.info("Unmonitoring {}", movie.title)
        }
    }
}