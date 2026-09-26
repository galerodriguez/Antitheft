plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Reconstruye la clave de firma (debug.keystore) desde su versión en texto base64.
// El binario no siempre sobrevive a Git/GitHub Desktop; el texto sí. Así la firma es
// SIEMPRE la misma y las actualizaciones se instalan encima sin "App no instalada".
run {
    val ksFile = file("debug.keystore")
    val ksB64 = file("debug.keystore.base64")
    if (!ksFile.exists() && ksB64.exists()) {
        ksFile.writeBytes(java.util.Base64.getMimeDecoder().decode(ksB64.readText()))
        println("debug.keystore reconstruido desde base64.")
    }
}

android {
    namespace = "com.sinaptic.centinela"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sinaptic.centinela"
        minSdk = 26          // Android 8.0 — foreground services modernos
        targetSdk = 34
        versionCode = 7
        versionName = "0.6.0"
        // Base del backend; sobrescribir por buildType para prod.
        buildConfigField("String", "API_BASE_URL", "\"https://api.centinela.example.com\"")
    }

    // Clave de firma FIJA: así todas las versiones se instalan encima de la anterior
    // (evita el error "App no instalada" y permite actualizaciones).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")

    // Firebase (push + base de datos en tiempo real + login)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // Ubicación y geocercas
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Cámara (foto del intruso)
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")

    // Red
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Seguridad (almacenamiento cifrado)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Corrutinas
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

// Aplica el plugin de Google Services SOLO si existe google-services.json.
// Así el proyecto compila incluso sin Firebase (la APK instala y abre; el push se activa
// cuando agregás tu google-services.json en app/).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    println("google-services.json encontrado: build CON Firebase (push habilitado).")
} else {
    println("Sin google-services.json: build SIN Firebase (push deshabilitado).")
}
