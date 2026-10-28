package dev.pranav.applock.core.update

data class UpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val patchNotes: String,
    val downloadUrl: String,
    val htmlUrl: String,
    val publishedAt: String = ""
)

sealed class UpdateResult {
    data class Available(val updateInfo: UpdateInfo) : UpdateResult()
    object UpToDate : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}
