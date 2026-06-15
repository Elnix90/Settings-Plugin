pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()

        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
    
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "compiler-plugin-template"

include("compiler-plugin")
include("gradle-plugin")
include("plugin-annotations")
