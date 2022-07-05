@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "NeoAuth2TgBot"

include(
    ":core",
    ":common",
    ":libs:mmkv"
)

buildCache { local { removeUnusedEntriesAfterDays = 3 } }
