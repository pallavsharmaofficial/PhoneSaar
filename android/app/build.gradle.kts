plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "dev.pallav.phonesaar"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.pallav.phonesaar"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Release key comes from env; without it the debug key signs so local builds work.
    val keystorePath = System.getenv("PHONESAAR_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PHONESAAR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PHONESAAR_KEY_ALIAS")
                keyPassword = System.getenv("PHONESAAR_KEYSTORE_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        // Compressed native libs: smaller download and no page-alignment quirks on OEM installers.
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/DEPENDENCIES")
    }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/rules"))
}

// The shared rules are the source of truth; the app ships a copy in assets.
val copyRules by tasks.registering(Copy::class) {
    from(rootProject.file("../rules/rules.json"))
    into(layout.buildDirectory.dir("generated/rules"))
}
tasks.named("preBuild") { dependsOn(copyRules) }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.81")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
