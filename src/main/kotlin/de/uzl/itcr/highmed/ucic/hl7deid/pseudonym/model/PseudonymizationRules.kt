package de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import java.io.FileNotFoundException

data class PseudonymizationRules(
    @JsonProperty("terser-paths-to-remove") val terserPathsToRemove: List<TerserRule>?,
    @JsonProperty("terser-paths-to-offset-date-time") val terserPathsToOffsetDateTime: List<TerserRule>?,
    @JsonProperty("terser-paths-to-replace-id") val terserPathsToReplaceId: List<TerserRule>?,
    @JsonProperty("normalized-tersers") val normalizedTersers: List<NormalizedTerser>?,
    @JsonProperty("terser-prefixes") val terserPrefixes: List<TerserPrefix>?
) {
    data class TerserRule(
        val terser: String,
        val desc: String
    )

    data class NormalizedTerser(
        val from: String,
        val to: String,
        val desc: String
    )

    data class TerserPrefix(
        @JsonProperty("msg-type") val msgType: String,
        val segments: List<String>,
        val value: String
    )

    companion object {
        private val objectMapper = ObjectMapper(YAMLFactory()).apply {
            this.registerKotlinModule()
        }

        private val log: Logger = LoggerFactory.getLogger(PseudonymizationRules::class.java)

        private fun resourceForRulesSettings(rulesSettings: PseudonymisationRulesSettings): Resource =
            when (rulesSettings.from) {
                PseudonymisationRulesSettings.FromEnum.FILESYSTEM -> rulesSettings.location?.let { loc ->
                    FileSystemResource(loc)
                } ?: throw IllegalArgumentException("there is no file system path resource provided")
                PseudonymisationRulesSettings.FromEnum.CLASSPATH -> ClassPathResource(
                    rulesSettings.location ?: "static/rules.yml"
                )
            }.also {
                if (!it.exists()) {
                    log.error("The specified rules resource $it does not exist.")
                    throw FileNotFoundException("The specified rules resource $it does not exist")
                }
                log.info("Loaded rules from $it")
            }

        fun readRules(rulesSettings: PseudonymisationRulesSettings): PseudonymizationRules {
            return objectMapper.readValue(resourceForRulesSettings(rulesSettings).inputStream)
        }
    }
}

@ConstructorBinding
@ConfigurationProperties(prefix = "hl7pseudo.rules")
data class PseudonymisationRulesSettings(
    val from: FromEnum = FromEnum.CLASSPATH,
    val location: String? = null
) {

    enum class FromEnum {
        CLASSPATH,
        FILESYSTEM
    }
}