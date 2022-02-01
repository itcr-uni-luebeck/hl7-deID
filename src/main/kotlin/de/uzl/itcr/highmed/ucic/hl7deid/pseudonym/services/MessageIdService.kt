package de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.services

import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo.MessageId
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo.MessageIdRepo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

@Service
class MessageIdService(
    @Autowired val messageIdRepo: MessageIdRepo
) {

    private val log: Logger = LoggerFactory.getLogger(PatientIdentityService::class.java)

    fun getPseudoIdForMessageControlId(messageControlId: String): String =
        messageIdRepo.getByMessageControlId(messageControlId)?.let {
            log.debug("retrieved message id: $it")
            return it.pseudoId
        } ?: run {
            val messageId = MessageId(messageControlId, UUID.randomUUID().toString())
            log.debug("Registered message ID: $messageId")
            return messageIdRepo.save(messageId).pseudoId
        }
}