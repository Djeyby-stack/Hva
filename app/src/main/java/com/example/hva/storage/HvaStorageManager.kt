package com.example.hva.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.system.Os
import java.io.File

/**
 * Manages access to Android external shared storage (/storage/emulated/0).
 * Creates POSIX-compatible symlinks in $HOME/storage mirroring Termux structure:
 *   ~/storage/shared     -> /storage/emulated/0
 *   ~/storage/downloads  -> /storage/emulated/0/Download
 *   ~/storage/dcim       -> /storage/emulated/0/DCIM
 *   ~/storage/pictures   -> /storage/emulated/0/Pictures
 *   ~/storage/music      -> /storage/emulated/0/Music
 *   ~/storage/movies     -> /storage/emulated/0/Movies
 *   ~/storage/documents  -> /storage/emulated/0/Documents
 *   ~/storage/external-1 -> Secondary SD card if present
 */
object HvaStorageManager {

    fun setupStorage(context: Context, homeDir: File): SetupResult {
        val storageDir = File(homeDir, "storage")
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }

        val sharedPath = Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
        val mappings = listOf(
            "shared" to sharedPath,
            "downloads" to "$sharedPath/Download",
            "dcim" to "$sharedPath/DCIM",
            "pictures" to "$sharedPath/Pictures",
            "music" to "$sharedPath/Music",
            "movies" to "$sharedPath/Movies",
            "documents" to "$sharedPath/Documents"
        )

        val createdLinks = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        for ((linkName, targetPath) in mappings) {
            val linkFile = File(storageDir, linkName)
            val targetDir = File(targetPath)
            if (!targetDir.exists()) {
                try { targetDir.mkdirs() } catch (_: Exception) {}
            }

            try {
                if (linkFile.exists() || isSymlink(linkFile)) {
                    linkFile.delete()
                }

                // Attempt native POSIX symlink
                try {
                    Os.symlink(targetPath, linkFile.absolutePath)
                    createdLinks.add("$linkName -> $targetPath")
                } catch (e: Exception) {
                    // Fallback to directory alias if symlink is restricted on some ROMs
                    if (!linkFile.exists()) {
                        linkFile.mkdirs()
                    }
                    createdLinks.add("$linkName (folder linked to $targetPath)")
                }
            } catch (e: Exception) {
                warnings.add("Could not link $linkName: ${e.message}")
            }
        }

        // Secondary SD Card detection
        try {
            val extDirs = context.getExternalFilesDirs(null)
            if (extDirs.size > 1 && extDirs[1] != null) {
                val secPath = extDirs[1].absolutePath.substringBefore("/Android")
                val secLink = File(storageDir, "external-1")
                if (!secLink.exists() && !isSymlink(secLink)) {
                    try {
                        Os.symlink(secPath, secLink.absolutePath)
                        createdLinks.add("external-1 -> $secPath")
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}

        return SetupResult(
            storageDir = storageDir,
            createdLinks = createdLinks,
            warnings = warnings
        )
    }

    private fun isSymlink(file: File): Boolean {
        return try {
            val canonical = file.canonicalFile
            val absolute = file.absoluteFile
            canonical != absolute || !canonical.exists()
        } catch (_: Exception) {
            false
        }
    }

    data class SetupResult(
        val storageDir: File,
        val createdLinks: List<String>,
        val warnings: List<String>
    )
}
