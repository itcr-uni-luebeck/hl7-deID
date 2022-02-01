package de.uzl.itcr.highmed.ucic.hl7analysis

import ca.uhn.hl7v2.HapiContext
import de.uzl.itcr.highmed.ucic.hl7analysis.messageindex.Hl7MessageRepo
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.services.Hl7PseudoymizationService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

val log: Logger = LoggerFactory.getLogger(Hl7UploadController::class.java)

@Controller
class Hl7UploadController(
    @Autowired val hapiContext: HapiContext,
    @Autowired val hl7PseudoymizationService: Hl7PseudoymizationService,
    @Autowired val hl7MessageRepo: Hl7MessageRepo
) {
    @PostMapping("/upload")
    fun handleFileUpload(
        @RequestParam("file") file: MultipartFile,
        model: MutableMap<String, Any>
    ): String {
        val hl7Text = file.inputStream.bufferedReader().readText()
        return handleHl7Message(hl7Text, model)
    }

    @GetMapping("/upload", "/text", "/search")
    fun redirectOnGet() = "redirect:/"

    private fun handleHl7Message(
        hl7Text: String,
        model: MutableMap<String, Any>
    ): String {
        val cleanedHl7Text = hl7Text.replace('\n', '\r')
        val pipeParser = hapiContext.pipeParser
        val xmlParser = hapiContext.xmlParser
        val message = pipeParser.parse(cleanedHl7Text)
        val pseudonymizedMessage = hl7PseudoymizationService.processMessage(cleanedHl7Text)
        model["pipe"] = cleanedHl7Text
        model["structure"] = message.printStructure()
        model["xml"] = xmlParser.encode(message)
        model["pseudo"] = xmlParser.encode(pseudonymizedMessage)
        return "viewHl7"
    }

    @PostMapping("/text")
    fun handleTextSubmission(
        @RequestParam("data") text: String,
        model: MutableMap<String, Any>
    ) = handleHl7Message(text, model)

    @GetMapping("/")
    fun uploadForm(
        model: Model
    ): String {
        model["msgTypes"] = hl7MessageRepo.getAvailableMsgTypes()
        model["triggerEvents"] = hl7MessageRepo.getAvailableTriggers()
        model["msgStructures"] = hl7MessageRepo.getAvailableMsgStructures()
        model["numMsg"] = hl7MessageRepo.countAll()
        return "landingForm"
    }
}