@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "TeleBotConsole"

include(
    ":core",
    ":common",
    ":libs:mmkv"
)

buildCache { local { removeUnusedEntriesAfterDays = 3 } }
