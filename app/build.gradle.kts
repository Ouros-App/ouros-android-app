import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.ouros.app"

    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.ouros.app"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { stream: InputStream ->
                localProperties.load(stream)
            }
        }

        fun resolveConfig(key: String, defaultValue: String = ""): String {
            return System.getenv(key)
                ?: localProperties.getProperty(key)
                ?: (project.findProperty(key) as? String)
                ?: defaultValue
        }

        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${resolveConfig("CLOUDINARY_CLOUD_NAME")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${resolveConfig("CLOUDINARY_UPLOAD_PRESET")}\"")
        buildConfigField("String", "CLOUDINARY_FOLDER", "\"${resolveConfig("CLOUDINARY_FOLDER", "ouros/profiles")}\"")
        buildConfigField("String", "OUROS_API_BASE_URL", "\"${resolveConfig("OUROS_API_BASE_URL", "https://ms-spring-api.discloud.app/")}\"")
        buildConfigField("String", "AUTH_API_BASE_URL", "\"${resolveConfig("AUTH_API_BASE_URL", "https://ms-auth-service.discloud.app/")}\"")
        buildConfigField("String", "DASHBOARD_BASE_URL", "\"${resolveConfig("DASHBOARD_BASE_URL", "https://ouros-dashboard.discloud.app/")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.register("syncInfisical") {
    group = "infisical"
    description = "Exports secrets from Infisical into local.properties using Universal Auth or CLI"
    doLast {
        val localPropsFile = rootProject.file("local.properties")
        val currentProps = Properties()
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { stream: InputStream ->
                currentProps.load(stream)
            }
        }

        fun getVal(key: String, default: String = ""): String {
            return System.getenv(key)
                ?: currentProps.getProperty(key)
                ?: (project.findProperty(key) as? String)
                ?: default
        }

        val clientId = getVal("INFISICAL_CLIENT_ID")
        val clientSecret = getVal("INFISICAL_CLIENT_SECRET")
        val projectId = getVal("INFISICAL_PROJECT_ID")
        val environment = getVal("INFISICAL_ENVIRONMENT", "dev")
        val secretPath = getVal("INFISICAL_SECRET_PATH", "/")
        val host = getVal("INFISICAL_URL", "https://app.infisical.com")

        if (clientId.isNotBlank() && clientSecret.isNotBlank() && projectId.isNotBlank()) {
            logger.lifecycle("Autenticando no Infisical ($host) via Universal Auth...")
            try {
                // 1. Universal Auth Login
                val loginUrl = URI("${host.trimEnd('/')}/api/v1/auth/universal-auth/login").toURL()
                val loginConn = loginUrl.openConnection() as HttpURLConnection
                loginConn.requestMethod = "POST"
                loginConn.setRequestProperty("Content-Type", "application/json")
                loginConn.doOutput = true
                loginConn.outputStream.use { os ->
                    val payload = """{"clientId":"$clientId","clientSecret":"$clientSecret"}"""
                    os.write(payload.toByteArray())
                }

                if (loginConn.responseCode !in 200..299) {
                    val err = loginConn.errorStream?.bufferedReader()?.readText().orEmpty()
                    logger.error("Falha na autenticação do Infisical (HTTP ${loginConn.responseCode}): $err")
                    return@doLast
                }

                val loginResponse = loginConn.inputStream.bufferedReader().readText()
                val tokenRegex = Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"")
                val token = tokenRegex.find(loginResponse)?.groupValues?.get(1)

                if (token.isNullOrBlank()) {
                    logger.error("Erro: accessToken não encontrado na resposta do Infisical.")
                    return@doLast
                }

                logger.lifecycle("✓ Autenticado! Buscando segredos do ambiente '$environment' em '$secretPath'...")

                // 2. Fetch Secrets
                val encodedPath = URLEncoder.encode(secretPath, "UTF-8")
                val secretsUrl = URI("${host.trimEnd('/')}/api/v3/secrets/raw?workspaceId=$projectId&environment=$environment&secretPath=$encodedPath&include_imports=true").toURL()
                val secretsConn = secretsUrl.openConnection() as HttpURLConnection
                secretsConn.requestMethod = "GET"
                secretsConn.setRequestProperty("Authorization", "Bearer $token")

                if (secretsConn.responseCode !in 200..299) {
                    val err = secretsConn.errorStream?.bufferedReader()?.readText().orEmpty()
                    logger.error("Falha ao buscar segredos (HTTP ${secretsConn.responseCode}): $err")
                    return@doLast
                }

                val secretsJson = secretsConn.inputStream.bufferedReader().readText()
                val secretPattern = Regex("\"secretKey\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"secretValue\"\\s*:\\s*\"([^\"]*)\"")
                val fetchedSecrets = mutableMapOf<String, String>()
                secretPattern.findAll(secretsJson).forEach { match ->
                    fetchedSecrets[match.groupValues[1]] = match.groupValues[2]
                }

                if (fetchedSecrets.isEmpty()) {
                    val altPattern = Regex("\"secretValue\"\\s*:\\s*\"([^\"]*)\"\\s*,\\s*\"secretKey\"\\s*:\\s*\"([^\"]+)\"")
                    altPattern.findAll(secretsJson).forEach { match ->
                        fetchedSecrets[match.groupValues[2]] = match.groupValues[1]
                    }
                }

                if (fetchedSecrets.isNotEmpty()) {
                    fetchedSecrets.forEach { (k, v) ->
                        currentProps.setProperty(k, v)
                    }

                    localPropsFile.outputStream().use { os ->
                        currentProps.store(os, "Atualizado automaticamente pelo Infisical Universal Auth")
                    }
                    logger.lifecycle("✓ ${fetchedSecrets.size} segredos sincronizados com sucesso do Infisical para o local.properties!")
                    return@doLast
                } else {
                    logger.warn("Aviso: Nenhum segredo retornado no caminho especificado.")
                }
            } catch (e: Exception) {
                logger.error("Erro ao conectar à API do Infisical: ${e.message}")
                return@doLast
            }
        }

        // Fallback: Infisical CLI
        val os = System.getProperty("os.name").lowercase()
        val isWindows = os.contains("win")
        val command = if (isWindows) {
            listOf("cmd", "/c", "infisical export --format=properties")
        } else {
            listOf("infisical", "export", "--format=properties")
        }

        try {
            val process = ProcessBuilder(command)
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.isNotBlank()) {
                val existingLines = if (localPropsFile.exists()) localPropsFile.readLines() else emptyList()
                val sdkLine = existingLines.firstOrNull { it.startsWith("sdk.dir") }

                val newContent = buildString {
                    if (sdkLine != null) {
                        appendLine(sdkLine)
                    }
                    appendLine()
                    appendLine("## Sincronizado automaticamente via Infisical CLI")
                    appendLine(output.trim())
                }
                localPropsFile.writeText(newContent)
                logger.lifecycle("✓ Segredos sincronizados com sucesso do Infisical CLI para local.properties!")
            } else {
                logger.warn("""
                    Para sincronizar automaticamente com o Infisical, configure as credenciais em local.properties:
                      INFISICAL_CLIENT_ID=...
                      INFISICAL_CLIENT_SECRET=...
                      INFISICAL_PROJECT_ID=...
                      INFISICAL_ENVIRONMENT=dev
                      INFISICAL_SECRET_PATH=/
                    e execute novamente: ./gradlew syncInfisical
                """.trimIndent())
            }
        } catch (e: Exception) {
            logger.warn("""
                Para sincronizar automaticamente com o Infisical, configure as credenciais em local.properties:
                  INFISICAL_CLIENT_ID=...
                  INFISICAL_CLIENT_SECRET=...
                  INFISICAL_PROJECT_ID=...
                  INFISICAL_ENVIRONMENT=dev
                  INFISICAL_SECRET_PATH=/
                e execute novamente: ./gradlew syncInfisical
            """.trimIndent())
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation("io.coil-kt:coil:2.7.0")
    implementation("com.github.yalantis:ucrop:2.2.11")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}