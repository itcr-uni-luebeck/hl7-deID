package de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.core.io.ClassPathResource

data class NameList(
    val english: EnglishNames,
    val street: StreetNames
) {
    data class EnglishNames(
        @JsonProperty("M") val maleNames: List<String>,
        @JsonProperty("F") val femaleNames: List<String>,
        @JsonProperty("family") val familyNames: List<String>
    )

    data class StreetNames(
        val type: List<String>,
        val secondary: List<String>
    )

    companion object {
        private val resourceFile = ClassPathResource("static/names.yml")
        private val objectMapper = ObjectMapper(YAMLFactory()).apply {
            this.registerKotlinModule()
        }

        fun readNameList(): NameList {
            return objectMapper.readValue(resourceFile.inputStream)
        }
    }

}
