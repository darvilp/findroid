data class FindroidTvVariant(
    val applicationId: String,
    val label: String,
    val versionName: String,
    val versionCode: Int,
)

fun findroidTvVariant(
    buildType: String,
    upstreamVersion: String,
    upstreamVersionCode: Int,
    revision: Int,
): FindroidTvVariant =
    if (buildType == "release") {
        FindroidTvVariant(
            applicationId = "dev.jdtech.jellyfin.atv",
            label = "Findroid TV",
            versionName = "$upstreamVersion-atv.$revision",
            versionCode = upstreamVersionCode * 1000 + revision,
        )
    } else {
        FindroidTvVariant(
            applicationId = "dev.jdtech.jellyfin.debug",
            label = "Findroid Debug",
            versionName = upstreamVersion,
            versionCode = upstreamVersionCode,
        )
    }

fun findroidTvAbiSplitsEnabled(taskNames: List<String>, universalApk: Boolean): Boolean {
    val isBuildingBundle = taskNames.any { it.lowercase().contains("bundle") }
    return !isBuildingBundle && !universalApk
}

fun findroidTvReleaseArtifactRequested(taskName: String): Boolean =
    taskName.matches(Regex("(?:assemble|bundle).*Release")) ||
        taskName.matches(Regex("package.*Release(?:Bundle|UniversalApk)?")) ||
        taskName.matches(Regex("sign.*Release(?:Bundle)?"))
