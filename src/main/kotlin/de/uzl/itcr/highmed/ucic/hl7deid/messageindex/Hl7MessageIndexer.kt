package de.uzl.itcr.highmed.ucic.hl7deid.messageindex

import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.HapiContext
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.model.Segment
import ca.uhn.hl7v2.model.v25.segment.MSH
import ca.uhn.hl7v2.model.v25.segment.PID
import ca.uhn.hl7v2.model.v25.segment.PV1
import ca.uhn.hl7v2.util.Terser
import de.uzl.itcr.highmed.ucic.hl7deid.log
import org.springframework.stereotype.Service
import java.io.File

@Service
class Hl7MessageIndexer(
    val hapiContext: HapiContext,
    val hl7MessageRepo: Hl7MessageRepo
) {

    fun indexHl7MessageString(messageString: String, filename: String?) =
        hapiContext.pipeParser
            .parse(messageString)?.let { message ->
                indexHl7Message(message, filename)
            } ?: throw HL7Exception("could not parse message")

    @Suppress("unused")
    fun indexHl7MessageFile(filename: String) =
        indexHl7MessageString(File(filename).readText(), filename)

    private inline fun <reified T : Segment> Terser.getSegment(): T? {
        return try {
            val segmentSpec = T::class.java.simpleName.uppercase()
            this.getSegment(segmentSpec) as T
        } catch (e: HL7Exception) {
            if (e.message?.contains("direct child") == true) {
                return null
            }
            throw e
        }
    }

    private fun indexHl7Message(message: Message, filename: String?): Hl7MessageEntity {
        val terser = Terser(message)
        //val msh = terser.getSegment("MSH") as MSH
        val msh = terser.getSegment<MSH>() ?: throw HL7Exception("no MSH segment found in message")
        val pid = terser.getSegment<PID>()
        val pv1 = terser.getSegment<PV1>()
        val messageType = msh.messageType.messageCode.value.uppercase()
        val messageId = msh.messageControlID.value ?: throw HL7Exception("Missing MSH-10 Message Control ID")
        val inRepo = hl7MessageRepo.findById(messageId)
        when {
            inRepo.isEmpty -> return hl7MessageRepo.save(
                Hl7MessageEntity(
                    messageId = messageId,
                    messageType = messageType,
                    triggerEvent = msh.messageType.triggerEvent.value,
                    messageStructure = msh.messageType.messageStructure.value,
                    patientId = pid?.let {
                        when {
                            it.patientIdentifierList.isNotEmpty() -> it.patientIdentifierList[0]?.idNumber?.value
                            else -> null
                        }
                    },
                    caseId = pv1?.visitNumber?.idNumber?.value,
                    filename = filename
                )
            ).also {
                log.info("Indexed message with ID $messageId")
            }
            else -> {
                log.debug("Message with ID $messageId was already registered")
                return inRepo.get()
            }
        }
    }

}