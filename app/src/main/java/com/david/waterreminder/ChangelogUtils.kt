package com.david.waterreminder

import android.content.Context

/**
 * A line of its own inside a `changelogs/<code>.txt`, separating the few lines a release leads
 * with from the rest of it. The "What's new" popup shows only what comes before it; the Change
 * Log screen shows the whole entry either way, marker line dropped.
 */
private const val HIGHLIGHTS_MARKER = "---"

data class ChangelogEntry(
    val versionCode: Int,
    val versionName: String,
    /** The whole entry, marker line removed. */
    val text: String,
    /** The lines before the marker — the whole entry for a file that has none. */
    val highlights: String,
    /** How many lines of [text] the highlights leave out. */
    val remaining: Int
)

/**
 * Reads `assets/changelogs/`, newest version first. Shared by the Change Log screen and the
 * "What's new" popup so a release is described in one place, and so a file the two parse
 * differently can't exist.
 */
fun Context.loadChangelogEntries(): List<ChangelogEntry> {
    val versionMap = assets.open("changelogs/versions.txt").bufferedReader()
        .readLines()
        .mapNotNull { line ->
            val parts = line.split("=")
            if (parts.size == 2) parts[0].trim().toIntOrNull()?.let { it to parts[1].trim() }
            else null
        }
        .toMap()

    return assets.list("changelogs")
        ?.filter { it != "versions.txt" }
        ?.mapNotNull { name ->
            val code = name.removeSuffix(".txt").toIntOrNull() ?: return@mapNotNull null
            val lines = assets.open("changelogs/$name").bufferedReader().readText()
                .trim()
                .lines()
                .filter { it.isNotBlank() }
            val markerAt = lines.indexOfFirst { it.trim() == HIGHLIGHTS_MARKER }
            val body = if (markerAt == -1) lines else lines.filterIndexed { i, _ -> i != markerAt }
            val highlights = if (markerAt == -1) body else lines.take(markerAt)
            ChangelogEntry(
                versionCode = code,
                versionName = versionMap[code] ?: code.toString(),
                text = body.joinToString("\n"),
                highlights = highlights.joinToString("\n"),
                remaining = body.size - highlights.size
            )
        }
        ?.sortedByDescending { it.versionCode }
        ?: emptyList()
}
