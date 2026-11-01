plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Compilador do Compose embutido no KGP 2.x (Task 121: as bibliotecas do
    // LastWave — kyant0:backdrop/shapes — são Compose; o plugin substitui o
    // antigo composeOptions.kotlinCompilerExtensionVersion)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tunegrab.app"
    // 34 → 36 (Task 121): o Compose + kyant0:backdrop do LastWave pedem
    // platform 36 no AAR metadata. targetSdk fica 34 — comportamento de
    // runtime do app NÃO muda (mesmas regras de sistema de antes)
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tunegrab.lg"
        minSdk = 24
        targetSdk = 34
        versionCode = 91
        versionName = "0.24.0-lg"

        // ABIs do motor yt-dlp embutido (python + ffmpeg são nativos).
        // x86/x86_64 ficam de fora para o APK não dobrar de tamanho —
        // aparelhos físicos são arm32/arm64.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        // NDK fixado: LAME é C puro, qualquer NDK moderno serve — fixar
        // evita o AGP tentar baixar o "default" dele do zero no CI/local.
        ndkVersion = "28.2.13676358"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/tunegrab.keystore")
            storePassword = "tunegrab2025"
            keyAlias = "tunegrab"
            keyPassword = "tunegrab2025"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        viewBinding = true
        // BuildConfig.VERSION_NAME na tela de boas-vindas e no rodapé do
        // Início (v0.18.8) — a versão mostrada nunca desatualiza
        buildConfig = true
        // Task 121: Compose entra via interop (ComposeView) — o app continua
        // View-based, mas as telas novas de vidro são Compose
        compose = true
    }

    packaging {
        // Exigido pelo youtubedl-android: os binários do python/ffmpeg precisam
        // ficar extraídos no disco (não dá para executar direto do APK).
        jniLibs {
            useLegacyPackaging = true
        }
        // LIXO DE PACOTE fora do APK (v0.19.14, versão final): os *.version
        // das libs (~48 arquivos de metadata), a info de VCS (textproto com o
        // hash do commit) e o app-metadata.properties do AGP — nada disso é
        // lido em runtime; é só rastro de build embutido de graça.
        resources {
            excludes += "META-INF/*.version"
            excludes += "META-INF/version-control-info.textproto"
            excludes += "META-INF/com/android/build/gradle/app-metadata.properties"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        abortOnError = false
    }
}

// Kotlin 2.x: o kotlinOptions virou compilerOptions no bloco kotlin{} —
// mesma forma que o LastWave usa no app deles.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.media:media:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil:2.6.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Task 121: setContent de Compose na Activity da letra (a extensão
    // ComposeView.setContent ficou internal no Compose 1.12; o caminho
    // público é o setContent do activity-compose, que o LastWave também usa)
    implementation("androidx.activity:activity-compose:1.9.1")

    // Tutorial de primeira abertura (v0.19.6): o pager deslizante do tour
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Player HD (v0.17.0): ExoPlayer/Media3 junta a faixa de vídeo DASH (até
    // 1080p, video-only) com a faixa de áudio no play do Início — o MediaPlayer
    // nativo só toca faixa muxada (vídeo+áudio juntos), e o YouTube não
    // entrega muxed acima de 360p/720p.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // Extração do YouTube — mesma engine usada pelo app NewPipe.
    // Fork próprio (https://github.com/micsxnn/NewPipeExtractor, tag v0.26.5-android4):
    //  1) compatibilidade Android < 13 (a v0.26.5 oficial usa URLDecoder/URLEncoder com
    //     Charset — Java 10 — e String.isBlank() — Java 11 —, que causam NoSuchMethodError
    //     em aparelhos antigos);
    //  2) clients extras de streams (TVHTML5 + visionOS + iOS) e fallback em cascata;
    //  3) suporte completo a PoTokenProvider: player request WEB com PoToken (destrava o
    //     bot-check "Sign in to confirm you're not a bot"), client iOS com PoToken e
    //     WEB+PoToken/iOS/visionOS/TV como fallback primário quando os clients anônimos falham.
    implementation("com.github.MicaelSanPedro:NewPipeExtractor:v0.26.5-android4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Motor de download yt-dlp EMBUTIDO no APK (python 3.12 + yt-dlp + ffmpeg),
    // a mesma engine usada pelo app Seal — o “plano A” do download na v0.4.0.
    // O yt-dlp é mantido semanalmente contra as mudanças do YouTube (rotação de
    // clients, desafios de JS via QuickJS embutido) e ainda é auto-atualizado
    // na primeira execução de cada versão do app (UpdateChannel.STABLE).
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    // ── LIQUID GLASS + LETRAS, as bibliotecas do LastWave (Task 121) ────────
    // Exatamente o kit que o LastWave-Native usa (gradle/libs.versions.toml
    // deles): Compose BOM 2026.06.01 + io.github.kyant0:backdrop 2.0.1 (o
    // Liquid Glass de verdade em Compose — blur/cor/lente em tempo real do
    // conteúdo de trás) + io.github.kyant0:shapes 1.2.1 (formas contínuas).
    // Entra via interop: o app continua View-based; as telas novas (letra
    // sincronizada estilo karaoke) são ComposeView dentro do layout XML.
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.github.kyant0:shapes:1.2.1")
}
