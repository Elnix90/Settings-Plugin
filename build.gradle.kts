plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.binary.compatibility.validator) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

allprojects {
    group = "io.github.elnix90.settings"
    version = "0.0.2"
}

subprojects {

    plugins.withId("com.vanniktech.maven.publish") {

        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {

            publishToMavenCentral()

            signAllPublications()

            pom {
                name.set(project.name)

                description.set(
                    "Settings compiler plugin"
                )

                inceptionYear.set("2025")

                url.set(
                    "https://github.com/elnix/Settings-Plugin"
                )

                licenses {
                    license {
                        name.set("MIT")
                        url.set(
                            "https://opensource.org/licenses/MIT"
                        )
                    }
                }

                developers {
                    developer {
                        id.set("elnix")
                        name.set("Elnix")
                    }
                }

                scm {
                    url.set(
                        "https://github.com/elnix/Settings-Plugin"
                    )

                    connection.set(
                        "scm:git:https://github.com/elnix/Settings-Plugin.git"
                    )

                    developerConnection.set(
                        "scm:git:ssh://git@github.com/elnix/Settings-Plugin.git"
                    )
                }
            }
        }
    }
}
