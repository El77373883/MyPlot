plugins { java }

group = "com.adrian"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.123-stable")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") { isTransitive = false }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar { archiveFileName.set("MyPlot-1.0.jar") }
