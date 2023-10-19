plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.5")
    implementation("com.kohlschutter.junixsocket:junixsocket-core:2.8.1")
    implementation("com.squareup.okio:okio:3.6.0")
}

tasks {
    val ENABLE_PREVIEW = "--enable-preview"
    withType<JavaCompile>() {
        options.compilerArgs.add(ENABLE_PREVIEW)
        // Optionally we can show which preview feature we use.
        options.compilerArgs.add("-Xlint:preview")
        // Explicitly setting compiler option --release
        // is needed when we wouldn't set the
        // sourceCompatiblity and targetCompatibility
        // properties of the Java plugin extension.
        options.release.set(21)
    }
    withType<Test>() {
        useJUnitPlatform()
        jvmArgs(ENABLE_PREVIEW)
    }
    withType<JavaExec>() {
        jvmArgs(ENABLE_PREVIEW)
    }
    withType<Test>() {
        useJUnitPlatform()
        jvmArgs(ENABLE_PREVIEW)
    }
    withType<JavaExec>() {
        jvmArgs(ENABLE_PREVIEW)
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}


application {
    // Define the main class for the application.
    mainClass.set("com.robeden.uds_proxy.App")
}

//tasks.named<Test>("test") {
//    useJUnitPlatform()
//}
