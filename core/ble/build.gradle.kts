plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.timemoredot.ble"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:protocol"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core)   // ContextCompat.registerReceiver(RECEIVER_NOT_EXPORTED), checkSelfPermission, LocationManagerCompat
    debugImplementation(testFixtures(project(":core:protocol")))   // 데모 저울 흉내(architecture.md 10.1.5): release에는 들어가지 않는다
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.serialization.json)   // 벡터 JSON 읽기(JsonElement만)
    testImplementation(testFixtures(project(":core:protocol")))
}

val protocolVectors = rootProject.file("docs/spec/protocol-vectors.json")
tasks.withType<Test>().configureEach {
    inputs.file(protocolVectors)
    systemProperty("protocol.vectors", protocolVectors.absolutePath)
}
