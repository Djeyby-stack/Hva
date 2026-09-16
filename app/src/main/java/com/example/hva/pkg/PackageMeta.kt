package com.example.hva.pkg

data class PackageMeta(
    val name: String,
    val version: String,
    val description: String,
    val sizeBytes: Long,
    val sha256: String,
    val dependencies: List<String> = emptyList(),
    val files: List<String> = emptyList()
)

data class InstalledPackage(
    val name: String,
    val version: String,
    val installDate: Long,
    val installedFiles: List<String>
)
