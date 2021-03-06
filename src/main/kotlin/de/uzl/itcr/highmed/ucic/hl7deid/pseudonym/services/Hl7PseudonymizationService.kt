package de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.services

import ca.uhn.hl7v2.DefaultHapiContext
import ca.uhn.hl7v2.HL7Exception
import ca.uhn.hl7v2.HapiContext
import ca.uhn.hl7v2.model.Message
import ca.uhn.hl7v2.model.v25.datatype.DTM
import ca.uhn.hl7v2.model.v25.message.ADT_AXX
import ca.uhn.hl7v2.model.v25.message.ORU_R01
import ca.uhn.hl7v2.model.v25.segment.PID
import ca.uhn.hl7v2.parser.CanonicalModelClassFactory
import ca.uhn.hl7v2.util.Terser
import ca.uhn.hl7v2.validation.impl.ValidationContextFactory
import de.uzl.itcr.highmed.ucic.hl7deid.messageindex.Hl7MessageIndexer
import de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.model.PseudonymizationRules
import de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.repo.PatientIdentity
import de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.repo.PatientPseudonym
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

@Service
class Hl7PseudoymizationService(
    @Autowired val hapiContext: HapiContext,
    @Autowired val patientIdentityService: PatientIdentityService,
    @Autowired val hl7MessageIndexer: Hl7MessageIndexer,
    @Autowired val messageIdService: MessageIdService,
    @Autowired val pseudonymIdService: PseudonymIdService,
    @Autowired val pseudonymizationRules: PseudonymizationRules
) {

    @Suppress("unused")
    fun moveFile(inputFileName: String, moveToDirectory: String) {
        val moveToFilename = Path.of(moveToDirectory)
            .resolve(Path.of(inputFileName).fileName).absolutePathString()
        Files.move(
            Path(inputFileName),
            Path(moveToFilename),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
        log.debug("Moved file from $inputFileName to $moveToFilename")
    }

    private val adtContext = DefaultHapiContext().apply {
        validationContext = ValidationContextFactory.noValidation()
        modelClassFactory = CanonicalModelClassFactory(ADT_AXX::class.java)
    }

    private val log: Logger = LoggerFactory.getLogger(Hl7PseudoymizationService::class.java)

    fun processAdtMessage(message: Message): ADT_AXX {
        val parsedMessage = adtContext.pipeParser.parse(hapiContext.pipeParser.encode(message)) as ADT_AXX
        // we need to re-parse the method as a generic ADT_AXX model instance, instead of the super-generic Message
        val terser = Terser(parsedMessage)
        val pidTerser: String = when (terser.get("/MSH-9-3")) {
            "ADT_A40" -> "/PATIENT/.PID"
            else -> "/PID"
        }
        return genericProcessMessage("ADT", parsedMessage, terser, pidTerser)
    }

    private fun <T> genericProcessMessage(messageType: String, parsedMessage: T, terser: Terser, pidTerser: String): T {
        val pidSegment = terser.getSegment(pidTerser) as PID
        val patient = extractPatientIdentity(pidSegment)
        val patientPseudonym = patientIdentityService.getOrGeneratePseudonym(patient)
        applyPseudonymPid(pidSegment, patientPseudonym)
        removeCompromisingElements(
            parsedMessage, messageType, terser, patientPseudonym.offset
        )
        return parsedMessage
    }

    private fun processMessage(message: Message): Message {
        val terser = Terser(message)
        applyMshControlId(terser)
        return when (val msgType = terser.get("MSH-9-1")) {
            "ADT" -> processAdtMessage(message)
            "ORU" -> when (val msgTrigger = terser.get("MSH-9-2")) {
                "R01" -> processOruR01Message(message as ORU_R01)
                else -> {
                    log.warn("ORU^$msgTrigger is not supported. The message may contain compromising PII")
                    genericProcessMessage(msgType, message, terser, "PID")
                }
            }
            else -> {
                log.warn("$msgType is not supported. The message may contain compromising PII")
                genericProcessMessage(msgType, message, terser, "PID")
            }
        }
    }

    fun resolveTerserPath(
        terserRule: PseudonymizationRules.TerserRule,
        msgType: String,
        terser: Terser
    ): List<PseudonymizationRules.TerserRule> {
        val prefixes = pseudonymizationRules.terserPrefixes.filter { it.msgType == msgType }
        val ruleSegment = terserRule.terser.substring(0, 3) // segment names are always 3 chars
        val modifiedRule = when (val applicable = prefixes.find { ruleSegment in it.segments }) {
            null -> terserRule
            else -> PseudonymizationRules.TerserRule("${applicable.value}${terserRule.terser}", terserRule.desc)
        }
        return applyRepetitions(modifiedRule, msgType, terser)
    }

    private fun applyRepetitions(
        terserRule: PseudonymizationRules.TerserRule,
        msgType: String,
        terser: Terser
    ): List<PseudonymizationRules.TerserRule> {
        val repetitionRules = pseudonymizationRules.terserRepetitions?.find { it.msgType == msgType }
            ?: return listOf(terserRule)
        val applicableRules = repetitionRules.repetitions.filter { it in terserRule.terser }
        return when {
            applicableRules.isEmpty() -> listOf(terserRule)
            else -> computeRepetitions(applicableRules, terserRule, terser)
        }
    }

    private fun computeRepetitions(
        applicableRules: List<String>,
        terserRule: PseudonymizationRules.TerserRule,
        terser: Terser
    ): List<PseudonymizationRules.TerserRule> {
        val returnRules = mutableListOf<PseudonymizationRules.TerserRule>()
        for (appliedRule in applicableRules) {
            var repetition = 0
            var hasThisRepetition = true
            val suffix = terserRule.terser.substringAfter(appliedRule)
            while (hasThisRepetition) {
                val repeatedTerser = "$appliedRule($repetition)$suffix"
                hasThisRepetition = try {
                    val value = terser.get(repeatedTerser)
                    // TODO: 18.02.2022 does not work
                    returnRules.add(PseudonymizationRules.TerserRule(repeatedTerser, terserRule.desc))
                    true
                } catch (_: Exception) {
                    false
                }
                repetition++
            }
        }
        return returnRules
    }

    private fun processOruR01Message(message: ORU_R01): ORU_R01 {
        val terser = Terser(message)
        //log.info(message.printStructure())
        return genericProcessMessage("ORU", message, terser, "PATIENT_RESULT/.PID")
    }

    private fun applyMshControlId(terser: Terser) {
        val mshControlId = terser.get("MSH-10")
        val newId = messageIdService.getPseudoIdForMessageControlId(mshControlId)
        terser.set("MSH-10", newId)
    }

    private fun parseHl7DateTimeString(stringDt: String, formatter: DateTimeFormatter): Temporal {
        return when (stringDt.length) {
            8 -> LocalDate.parse(stringDt, formatter)
            12, 14 -> LocalDateTime.parse(stringDt, formatter)
            else -> throw NotImplementedError("no date time formatter for: $stringDt (length ${stringDt.length})")
        }
    }

    private fun getFormatterStringDt(stringDt: String): DateTimeFormatter? {
        val formatterPattern = when (stringDt.length) {
            8 -> "yyyyMMdd"
            12 -> "yyyyMMddHHmm"
            14 -> "yyyyMMddHHmmss"
            else -> throw NotImplementedError("no date time formatter for: $stringDt (length ${stringDt.length})")
        }
        return DateTimeFormatter.ofPattern(formatterPattern)
    }

    private fun <T> removeCompromisingElements(
        message: T,
        messageType: String,
        terser: Terser,
        dateTimeOffset: Duration,
    ): T {

        fun replaceTerserValue(
            path: String,
            logOperation: String,
            logDescription: String,
            body: (value: String, description: String) -> String?
        ) {
            try {
                val originalValue = terser.get(path) ?: return
                val transformedValue = body(originalValue, logDescription) ?: return
                terser.set(path, transformedValue)
                log.debug("$logOperation $path ($logDescription)")
            } catch (e: HL7Exception) {
                when {
                    e.message?.contains("Can't find") == true -> log.debug("$path: not applied, missing segment")
                    e.message?.contains("there are currently only") == true -> log.debug("$path: not applied, missing reps")
                    else -> throw e
                }
            }
        }

        fun applyOperationToTerserRules(
            rules: List<PseudonymizationRules.TerserRule>,
            logOperation: String,
            terser: Terser,
            body: (value: String, description: String, terserPath: String) -> String?
        ) = run {
            val prefixedRules = rules.map { terserRule ->
                resolveTerserPath(terserRule, messageType, terser)
            }
            prefixedRules.forEach { rules ->
                rules.forEach { rule ->
                    replaceTerserValue(rule.terser, logOperation, rule.desc) { value, _ ->
                        body(value, rule.desc, rule.terser)
                    }
                }
            }
        }


        applyOperationToTerserRules(pseudonymizationRules.terserPathsToRemove, "Removed", terser)
        { _, description, _ ->
            "**REMOVED** ($description)"
        }

        applyOperationToTerserRules(
            pseudonymizationRules.terserPathsToOffsetDateTime,
            "Applied D/T offset to",
            terser
        )
        { value, _, _ ->
            val dtFormatter = getFormatterStringDt(value) ?: return@applyOperationToTerserRules null
            val dt = parseHl7DateTimeString(value, dtFormatter)
            val offsetDt = dt + dateTimeOffset
            dtFormatter.format(offsetDt)
        }

        applyOperationToTerserRules(
            pseudonymizationRules.terserPathsToReplaceId,
            "Replaced ID",
            terser
        )
        { value, _, terserPath ->
            pseudonymIdService.getPseudonymIdForTerser(terserPath, value)?.replacedValue.toString()
        }

        return message
    }

    fun processMessage(stringMessage: String, filename: String? = null): Message {
        val cleanMessageString = stringMessage.cleanCrLf()
        try {
            hl7MessageIndexer.indexHl7MessageString(stringMessage, filename)
        } catch (e: HL7Exception) {
            de.uzl.itcr.highmed.ucic.hl7deid.log.warn(e.message)
        }
        val parsedMessage = hapiContext.pipeParser.parse(cleanMessageString)
        return processMessage(parsedMessage)
    }

    fun processMessageByFilename(filename: String) = Path.of(filename).toFile().let { file ->
        processMessage(
            file.readText(StandardCharsets.ISO_8859_1), file.name
        )
    }

    @Suppress("unused")
    fun processFromFileToFile(inputFileName: String, outputDirectory: String, changeFilenameToMsgId: Boolean) =
        processMessageByFilename(inputFileName).let { processed ->
            val encoded = hapiContext.pipeParser.encode(processed)
            val outputFilename = Path.of(outputDirectory).resolve(
                when (changeFilenameToMsgId) {
                    true -> Path.of(outputDirectory).resolve("${Terser(processed).get("MSH-10")}.hl7")
                    else -> Path.of(inputFileName).fileName
                }
            )
            val file = outputFilename.toFile()
            file.createNewFile()
            file.writeText(
                encoded,
                StandardCharsets.ISO_8859_1
            )
            log.debug("Wrote pseudo message to $outputFilename")
        }

    private fun applyPseudonymPid(pidSegment: PID, patientPseudonym: PatientPseudonym) {
        pidSegment.dateTimeOfBirth?.let { dob ->
            patientPseudonym.dateOfBirth?.let { pseudodob ->
                dob.time?.setDatePrecision(
                    pseudodob.year,
                    pseudodob.monthValue,
                    pseudodob.dayOfMonth
                )
            }
            pidSegment.patientName.firstOrNull()?.let {
                it.familyName.surname.value = patientPseudonym.lastName
            }
            pidSegment.patientName?.firstOrNull()?.let {
                it.givenName.value = patientPseudonym.firstName
            }
        }
    }

    private fun extractPatientIdentity(pidSegment: PID): PatientIdentity {
        val patientIdList = pidSegment.patientIdentifierList.map {
            it.idNumber.toString()
        }
        val lastName = pidSegment.patientName?.firstOrNull()?.familyName?.surname?.toString()
        val givenName = pidSegment.patientName?.firstOrNull()?.givenName?.toString()
        val administrativeSex = pidSegment.administrativeSex?.value
        val dateOfBirth =
            if (pidSegment.dateTimeOfBirth?.toString() != "TS[]") pidSegment.dateTimeOfBirth?.time?.parseLocalDate() else null
        return patientIdentityService.getOrStorePatient(
            patientIdList = patientIdList,
            lastName = lastName,
            firstName = givenName,
            gender = parseAdministrativeSex(administrativeSex),
            dateOfBirth = dateOfBirth
        )
    }

    private fun parseAdministrativeSex(input: String?): PatientIdentity.AdministrativeSex? = when (input?.lowercase()) {
        null -> null
        "m" -> PatientIdentity.AdministrativeSex.MALE
        "w", "f" -> PatientIdentity.AdministrativeSex.FEMALE
        "o", "d", "u", "a", "n" -> PatientIdentity.AdministrativeSex.OTHER
        else -> PatientIdentity.AdministrativeSex.OTHER
    }
}

private fun String.cleanCrLf(): String =
    Regex("\n").replace(this, "\r")

fun DTM.parseLocalDate(): LocalDate = valueAsCalendar
    .toInstant()
    .atZone(ZoneId.systemDefault())
    .toLocalDate()