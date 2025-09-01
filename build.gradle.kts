plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    `maven-publish`
    signing
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // SWT dependencies
    implementation("org.eclipse.platform:org.eclipse.swt:3.130.0")
    
    // Kotlin standard library
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    
    
    // Optional: Reflection for advanced widget inspection
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // Testing framework integration - available at runtime for layout assertions  
    implementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

kotlin {
    jvmToolchain(11)
}

tasks.test {
    useJUnitPlatform()
    
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// Configure SWT platform dependencies
configurations.all {
    resolutionStrategy {
        dependencySubstitution {
            val os = System.getProperty("os.name").toLowerCase()
            when {
                os.contains("windows") -> {
                    substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
                        .using(module("org.eclipse.platform:org.eclipse.swt.win32.win32.x86_64:3.108.0"))
                }
                os.contains("linux") -> {
                    substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
                        .using(module("org.eclipse.platform:org.eclipse.swt.gtk.linux.x86_64:3.108.0"))
                }
                os.contains("mac") -> {
                    substitute(module("org.eclipse.platform:org.eclipse.swt.\${osgi.platform}"))
                        .using(module("org.eclipse.platform:org.eclipse.swt.cocoa.macosx.x86_64:3.108.0"))
                }
            }
        }
    }
}

// Java source and target compatibility for published artifacts
java {
    withJavadocJar()
    withSourcesJar()
}

// Maven publishing configuration
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = "io.github.ddsimoes"
            artifactId = "autoswt"
            version = "0.1.0"
            
            pom {
                name.set("AutoSWT")
                description.set("A lightweight, JUnit-integrated testing framework for SWT-based UIs with thread-safe test automation and comprehensive debugging tools")
                url.set("https://github.com/ddsimoes/auto-swt")
                
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("ddsimoes")
                        name.set("Daniel Simoes")
                        email.set("daniel@example.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/ddsimoes/auto-swt.git")
                    developerConnection.set("scm:git:ssh://github.com/ddsimoes/auto-swt.git")
                    url.set("https://github.com/ddsimoes/auto-swt")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ddsimoes/auto-swt")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
}

// Signing configuration for published artifacts
signing {
    setRequired({
        // Only require signing if we have signing keys and we're publishing to a remote repository
        gradle.taskGraph.hasTask("publish") && (
            project.hasProperty("signing.keyId") || 
            project.hasProperty("signingKey") || 
            System.getenv("SIGNING_KEY") != null
        )
    })
    
    val signingKey: String? by project
    val signingPassword: String? by project
    
    if (project.hasProperty("signingKey") || System.getenv("SIGNING_KEY") != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    
    sign(publishing.publications["maven"])
}