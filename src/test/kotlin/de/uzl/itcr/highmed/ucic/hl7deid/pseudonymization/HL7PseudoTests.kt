package de.uzl.itcr.highmed.ucic.hl7deid.pseudonymization

import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.util.Terser
import de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.services.Hl7PseudoymizationService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration
class HL7PseudoTests(
    @Autowired val hl7PseudoymizationService: Hl7PseudoymizationService,
    @Autowired val hapiContext: DefaultHapiContext
) {

    private final val messageString = "MSH|^~\\&|junit||pseudo||20220201112815||ADT^A01|GyY4F6kLyC7NwHDnqAmAx252|P|2.5\r" +
            "PID||42|||Thought^Deep^^^^PHD||20010525|F|Computer^Big||Deep Thought Ave. 1^^Computer City^^^Magrathea\r" +
            "PV1||I|||||1042^Slatibartfast|1000^Prefect^Ford^^^^MD|||||||||||424242|||||||||||||||||||||||||20220201113603"
    private final val messageUnderTest get() = hapiContext.pipeParser.parse(messageString)

    @Test
    fun `pseudo changes message`() {
        val processedMessage = hl7PseudoymizationService.processMessage(messageString)
        assertThat(hapiContext.pipeParser.encode(processedMessage)).isNotEqualTo(messageString)
    }

    /*@Test
    fun `can access names`() = (messageUnderTest as ADT_A01).let { adtA01 ->
       adtA01.names.forEach { strucName ->
           if (adtA01.isRepeating(strucName)) {
               return@forEach
           }
           val segment = adtA01.get(strucName) as Segment
           for (fieldId in 1 .. segment.numFields()) {
               val field = segment.getField(fieldId)
               field.map {
                   println(it.name)
               }
           }
       }
    }*/

    @Test
    fun `all rules considered`() {
        val processedTerser = Terser(hl7PseudoymizationService.processAdtMessage(messageUnderTest))
        val mutTerser = Terser(messageUnderTest)
        hl7PseudoymizationService.pseudonymizationRules.allRules.forEach { rule ->
            val resolvedRule = hl7PseudoymizationService.resolveTerserPath(rule, "ADT")
            val terserPath = resolvedRule.terser
            try {
                val mutValue: String? = mutTerser.get(terserPath)
                val processedValue: String? = processedTerser.get(terserPath)
                when {
                    mutValue == null -> assertThat(processedValue).isNull()
                    processedValue == null -> assertThat(mutValue).isNull()
                    else -> assertThat(mutValue).isNotEqualTo(processedValue)
                }
            } catch (e: HL7Exception) {
                when {
                    e.message?.contains("Can't find") ?: false -> assertThrows<HL7Exception> {
                        mutTerser.get(terserPath)
                        processedTerser.get(terserPath)
                    }
                    else -> fail("unhandled HL7 exception: ${e.message}")
                }
            }
        }
    }
}