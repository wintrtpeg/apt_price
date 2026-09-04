import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * 공공데이터포털(data.go.kr) 인증키는 저장소에 커밋하지 않는다.
 * local.properties 의 MOLIT_SERVICE_KEY 또는 동일 이름의 환경변수에서 읽어 BuildConfig 로 주입한다.
 * 키가 없으면 빈 문자열이 주입되며, 앱은 "인증키 미설정" 상태를 사용자에게 그대로 노출한다.
 * (키가 없다고 해서 임의의 더미 거래 데이터를 만들어 채우지 않는다.)
 */
val molitServiceKey: String = run {
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    localProps.getProperty("MOLIT_SERVICE_KEY")
        ?: System.getenv("MOLIT_SERVICE_KEY")
        ?: ""
}

android {
    namespace = "com.aptprice.tracker"
    compileSdk = 35

    /**
     * 디버그 서명 키를 저장소에 고정해 둔다.
     *
     * 기본 동작은 빌드하는 기기마다 debug.keystore 를 새로 만드는 것이다. CI 러너는
     * 매번 새로 뜨므로 빌드할 때마다 서명이 달라지고, 그러면 기존에 깔린 앱 위에
     * 덮어쓰기 설치가 되지 않는다("서명이 일치하지 않습니다"). 매 업데이트마다 앱을
     * 지우고 다시 깔아야 하고, 그때마다 저장해 둔 인증키도 함께 사라진다.
     *
     * 키를 고정하면 모든 빌드가 같은 서명을 갖는다.
     *
     * 이 키는 디버그 전용이고 비밀번호도 공개된 관례값(android)이다. 배포용 서명에는
     * 쓸 수 없으므로 저장소에 두어도 된다. 스토어 배포를 하게 되면 릴리스 키는 반드시
     * 따로 만들고 Secrets 로만 다뤄야 한다.
     */
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.aptprice.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MOLIT_SERVICE_KEY", "\"$molitServiceKey\"")
        buildConfigField(
            "String",
            "MOLIT_BASE_URL",
            "\"https://apis.data.go.kr/1613000/\"",
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    // Room 스키마를 파일로 뽑아 둔다. 나중에 마이그레이션을 쓸 때 기준이 된다.
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
