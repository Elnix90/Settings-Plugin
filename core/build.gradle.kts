import com.android.build.api.dsl.LibraryExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.maven.publish)
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

extensions.configure<LibraryExtension> {
    namespace = "io.github.elnix90.core"

    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt())
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.datastore.core)
    implementation(libs.timber)
    implementation(libs.kotlinx.serialization.json)

    api(libs.androidx.datastore.preferences.core)
    api(libs.kotlinx.coroutines.core)

    implementation(libs.dragon.logging)
    implementation(project(":annotations"))
}
