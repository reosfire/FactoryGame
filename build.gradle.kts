import korlibs.korge.gradle.*
import korlibs.korge.gradle.targets.jvm.*

plugins {
	alias(libs.plugins.korge)
}

korge {
	id = "ru.reosfire.factorygame"

// To enable all targets at once

	//targetAll()

// To enable targets based on properties/environment variables
	//targetDefault()

// To selectively enable targets
	
	targetJvm()
	targetJs()

	serializationJson()
}

tasks.named<KorgeJavaExec>("runJvm") {
    logLevel = "trace"
}

dependencies {
    add("commonMainApi", project(":deps"))
    //add("commonMainApi", project(":korge-dragonbones"))
}

