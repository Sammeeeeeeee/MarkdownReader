plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sam.markdownreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sam.markdownreader"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"
    }

    signingConfigs {
        // A stable release identity so updates install in place (Obtainium etc.).
        // The committed keystore is public, so it authenticates nothing — it only
        // keeps the signature consistent across CI runs. Point the env vars at a
        // private keystore to harden it.
        create("release") {
            storeFile = file(System.getenv("RELEASE_KEYSTORE") ?: "$rootDir/signing/release.keystore")
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "markdownreader"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "markdownreader"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "markdownreader"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.jlatexmath.android)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.gfm.alerts)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.footnotes)
    implementation(libs.commonmark.ext.task.list.items)
    implementation(libs.commonmark.ext.ins)
    implementation(libs.commonmark.ext.yaml.front.matter)
    implementation(libs.commonmark.ext.image.attributes)
    testImplementation(libs.junit)
}
