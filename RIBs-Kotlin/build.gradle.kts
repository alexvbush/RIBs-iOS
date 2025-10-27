plugins {
    kotlin("multiplatform") version "2.0.0"
    id("com.android.library") version "8.3.0"
    `maven-publish`
}

group = "com.uber.rib"
version = "0.17.0"

kotlin {
    jvmToolchain(17)

    // Android target
    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    // iOS targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // JVM target (for desktop/server)
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // RxJava/RxSwift equivalent for multiplatform
                api("io.reactivex.rxjava3:rxjava:3.1.8")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.8.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
                implementation("androidx.core:core-ktx:1.12.0")
            }
        }

        val iosMain by creating {
            dependsOn(commonMain)
        }

        val iosX64Main by getting {
            dependsOn(iosMain)
        }

        val iosArm64Main by getting {
            dependsOn(iosMain)
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.reactivex.rxjava3:rxkotlin:3.0.1")
            }
        }
    }
}

android {
    namespace = "com.uber.rib"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }
}

publishing {
    publications {
        publications.withType<MavenPublication> {
            pom {
                name.set("RIBs")
                description.set("Uber's cross-platform mobile architecture - Kotlin Multiplatform implementation")
                url.set("https://github.com/uber/RIBs")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
}
