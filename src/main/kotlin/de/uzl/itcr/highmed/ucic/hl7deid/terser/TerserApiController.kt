package de.uzl.itcr.highmed.ucic.hl7analysis.terser

import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.HapiContext
import ca.uhn.hl7v2.util.Terser
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.endpoint.invoke.MissingParametersException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.MissingRequestValueException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@Controller
class TerserApiController(
    @Autowired val hapiContext: HapiContext
) {

    @PostMapping(
        "/api/terser",
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun handleTerserRequest(
        @RequestParam paramMap: MultiValueMap<String, String>
    ): ResponseEntity<ApiResult> = try {
        paramMap["msg"]?.let { msg ->
            val message = hapiContext.xmlParser.apply {
                this.parserConfiguration.isValidating = false
            }.parse(msg.first())
            val terser = Terser(message)
            paramMap["terser"]?.let { terserString ->
                return ResponseEntity.ok(ApiResult(terser.get(terserString.first()) ?: "null"))
            } ?: throw MissingRequestValueException("missing terser parameter")
        } ?: throw MissingRequestValueException("missing msg parameter")
    } catch (e: Exception) {
        ResponseEntity.ok(ApiResult(e.message ?: "unknown exception"))
    }

    data class ApiResult(
        val result: String
    )
}