import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)   // 벡터 JSON 파싱(JsonElement만 써서 플러그인 불필요)
}

val protocolVectors = rootProject.file("docs/spec/protocol-vectors.json")
tasks.test {
    inputs.file(protocolVectors)   // 벡터가 바뀌면 테스트를 다시 돈다
    systemProperty("protocol.vectors", protocolVectors.absolutePath)
}
