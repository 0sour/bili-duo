import org.gradle.api.tasks.testing.Test
import java.util.Properties
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

// Signing credentials stay outside the APK and outside version control.
// See keystore.properties.example; without the local file release stays unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "cn.xfangfang.biliduo"
    compileSdk = 34

    defaultConfig {
        applicationId = "cn.xfangfang.biliduo"
        minSdk = 24
        targetSdk = 34
        versionCode = 58
        versionName = "0.2.0-rc57"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")

    implementation("me.saket.telephoto:zoomable:0.12.1")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")

    implementation("com.google.zxing:core:3.5.3")

    implementation("com.github.jeziellago:compose-markdown:0.5.7")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
}

/*
 * The project lives under a non-ASCII path (D:\项目\...). Gradle writes the
 * unit-test classpath into a UTF-8 @argfile, but JDK 17 on Chinese Windows
 * reads that file with the platform charset, so every test class looks missing.
 * A pathing jar keeps the worker command line short and moves the long
 * classpath into the manifest, which Java always parses as UTF-8.
 */
afterEvaluate {
    val testTask = tasks.named<Test>("testDebugUnitTest").get()
    val originalClasspath = testTask.classpath.files.toList().distinctBy { it.absolutePath }
    // Keep the jar outside the non-ASCII project path: the @argfile that Gradle
    // writes for the worker itself is still decoded with the platform charset.
    val pathingJarFile = gradle.gradleUserHomeDir.resolve("tmp/bili-duo-unit-test-pathing.jar")

    val pathingJar = tasks.register("unitTestPathingJar") {
        outputs.file(pathingJarFile)
        outputs.upToDateWhen { false }
        doLast {
            val manifest = Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                mainAttributes[Attributes.Name.CLASS_PATH] =
                    originalClasspath.joinToString(" ") { it.toURI().toASCIIString() }
            }
            pathingJarFile.parentFile.mkdirs()
            JarOutputStream(pathingJarFile.outputStream(), manifest).use { }
        }
    }

    testTask.dependsOn(pathingJar)
    // The custom classpath no longer carries AGP's normal main-class dependency.
    testTask.dependsOn("bundleDebugClassesToRuntimeJar")
    testTask.dependsOn("compileDebugUnitTestKotlin")
    // The custom classpath hides AGP's normal task dependencies, so always run
    // tests instead of relying on up-to-date checks for the pathing jar.
    testTask.outputs.upToDateWhen { false }
    val junitJars = originalClasspath.filter {
        it.name.startsWith("junit-") || it.name.startsWith("hamcrest-")
    }
    testTask.classpath = files(pathingJarFile, junitJars)
}
