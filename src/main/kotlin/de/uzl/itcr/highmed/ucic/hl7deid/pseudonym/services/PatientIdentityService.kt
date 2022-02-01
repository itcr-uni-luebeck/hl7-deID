package de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.services

import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo.PatientPseudonym
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo.PatientPseudonymRepo
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.model.NameList
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo.PatientIdentity
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo.PatientIdentityRepo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.*

@Service
class PatientIdentityService(
    @Autowired val patientIdentityRepo: PatientIdentityRepo,
    @Autowired val patientPseudonymRepo: PatientPseudonymRepo,
    @Autowired val nameList: NameList,
    @Autowired val random: SecureRandom
) {

    private val log: Logger = LoggerFactory.getLogger(PatientIdentityService::class.java)

    fun getOrStorePatient(
        patientIdList: List<String>,
        lastName: String?,
        firstName: String?,
        gender: PatientIdentity.AdministrativeSex?,
        dateOfBirth: LocalDate?
    ): PatientIdentity {
        val retrievedIdentities = patientIdList.mapNotNull {
            patientIdentityRepo.getByPatientIdList(it)
        }
        return when (retrievedIdentities.any()) {
            false -> patientIdentityRepo.save(
                PatientIdentity(
                    patientIdList = patientIdList,
                    lastName = lastName,
                    firstName = firstName,
                    dateOfBirth = dateOfBirth,
                    administrativeSex = gender
                ).also {
                    log.info("Registered patient with identifier(s) $patientIdList")
                }
            )
            else -> retrievedIdentities.first().also {
                log.info("Retrieved patient with identifier(s) $patientIdList")
            }
        }
    }

    fun getOrGeneratePseudonym(identity: PatientIdentity): PatientPseudonym =
        when (val pseudonym = patientPseudonymRepo.getByIdentity(identity)) {
            null -> {
                patientPseudonymRepo.save(generatePseudonym(patientIdentity = identity)).also {
                    log.info("Generated pseudonym: '${it.lastName}, ${it.firstName}' using offset: ${it.offset}")
                }
            }
            else -> pseudonym
        }

    fun generatePseudonym(patientIdentity: PatientIdentity): PatientPseudonym {
        val lastName = generateNameFragment(nameList.english.familyNames)
        val firstName = (when (patientIdentity.administrativeSex) {
            PatientIdentity.AdministrativeSex.MALE -> nameList.english.maleNames
            PatientIdentity.AdministrativeSex.FEMALE -> nameList.english.femaleNames
            null, PatientIdentity.AdministrativeSex.OTHER -> when (random.nextBoolean()) {
                true -> nameList.english.maleNames
                else -> nameList.english.femaleNames
            }
        }).let {
            generateNameFragment(it)
        }
        val dateOfBirth = patientIdentity.dateOfBirth?.let { modifyDateOfBirth(it) }
        val hoursOffset = random.longs(96, 481).findAny().asLong
        val signedHoursOffset = hoursOffset * (if (random.nextBoolean()) -1 else 1)
        val minutesOffset = random.longs(-480, 481).findAny().asLong
        val dateTimeOffset = Duration.ofHours(signedHoursOffset) + Duration.ofMinutes(minutesOffset)
        return PatientPseudonym(
            identity = patientIdentity,
            lastName = lastName,
            firstName = firstName,
            dateOfBirth = dateOfBirth,
            dateTimeOffset = dateTimeOffset.toString()
        )
    }

    fun generateNameFragment(concreteNameList: List<String>) =
        "${concreteNameList.takeRandom(random)}${random.nextInt(10240)}"

    fun modifyDateOfBirth(originalDate: LocalDate): LocalDate {
        val zoneId = ZoneOffset.UTC as ZoneId
        val year = originalDate.year
        val startOfYear = LocalDate.of(
            year,
            1,
            1
        ).atStartOfDay(zoneId).toInstant()
        val endOfYear = LocalDate.of(
            year,
            12,
            31
        ).atStartOfDay(zoneId).toInstant()
        var newDateOfBirth: LocalDate? = null
        val randomEpochSeconds = random.longs(startOfYear.epochSecond, endOfYear.epochSecond)
        while (newDateOfBirth == null || newDateOfBirth.dayOfYear == originalDate.dayOfYear) {
            newDateOfBirth = LocalDate.ofInstant(
                Instant.ofEpochSecond(randomEpochSeconds.findAny().asLong),
                zoneId
            )
        }
        return newDateOfBirth
    }
}

fun <T> List<T>.takeRandom(random: SecureRandom): T = this[random.nextInt(this.size)]