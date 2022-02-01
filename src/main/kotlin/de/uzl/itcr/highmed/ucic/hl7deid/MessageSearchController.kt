package de.uzl.itcr.highmed.ucic.hl7deid

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import de.uzl.itcr.highmed.ucic.hl7deid.messageindex.Hl7MessageRepo
import de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.repo.MessageIdRepo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/search")
class MessageSearchController(
    @Autowired private val messageRepo: Hl7MessageRepo,
    @Autowired private val messageIdRepo: MessageIdRepo,
) {

    private val log: Logger = LoggerFactory.getLogger(MessageSearchController::class.java)

    @PostMapping
    fun handleSearch(
        searchParameters: Hl7MessageRepo.SearchParameters,
        model: Model
    ): String {
        val resultMessages = messageRepo.findByParameters(searchParameters)
        model["hasResult"] = resultMessages.any()
        model["result"] = resultMessages.map {
            toMap(it) + mapOf("pseudoMessageId" to messageIdRepo.getByMessageControlId(it.messageId)?.pseudoId)
        }
        model["searchParams"] = toMap(searchParameters)
        return "searchResult"
    }

    private fun toMap(data: Any) = ObjectMapper().convertValue(data, object : TypeReference<Map<String, Any>>() {})


}