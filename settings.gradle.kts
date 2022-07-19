@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "TeleBotConsole"

include(
    ":core",
    ":common",
    ":libs:mmkv",
    ":plugins"
)

buildCache { local { removeUnusedEntriesAfterDays = 3 } }
