import dev.kikugie.stonecutter.StonecutterSettings

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.4.6"
    id("org.moddedmc.wiki.toolkit") version "+" apply false
}

extensions.configure<StonecutterSettings> {
    kotlinController = true
    centralScript = "build.gradle.kts"
    shared {
        fun mc(mcVersion: String, name: String = mcVersion, loaders: Iterable<String>) {
            for (loader in loaders) {
                vers("$name-$loader", mcVersion)
            }
        }

        // Set this to true to speed up build times when you're working on the documentation.
        val IS_DOCUMENTATION_MODE = true;

        if (IS_DOCUMENTATION_MODE) {
            mc("1.21.4", loaders = listOf("fabric"))
        } else {
            mc("1.21.4", loaders = listOf("fabric", "neoforge"))
            mc("1.21.2", loaders = listOf("fabric", "neoforge"))
            mc("1.21", loaders = listOf("fabric", "neoforge"))
            mc("1.20.6", loaders = listOf("fabric", "neoforge"))
            mc("1.20.4", loaders = listOf("fabric", "neoforge"))
            mc("1.20.1", loaders = listOf("fabric", "forge"))
        }
    }
    create(rootProject)
}
rootProject.name = "YetAnotherConfigLib"
