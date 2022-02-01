package de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.services

import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.model.PseudonymizationRules
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo.PseudonymId
import de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo.NumericIdRepo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.*

@Service
class PseudonymIdService(
    @Autowired val numericIdRepo: NumericIdRepo,
    @Autowired val random: SecureRandom,
    @Autowired val pseudonymizationRules: PseudonymizationRules
) {

    private fun generateLongPseudonym(normalizedTerserPath: String, originalValue: String, originalLong: Long): Long {
        var idIsUnique = false
        var newId: Long? = null
        while (!idIsUnique) {
            val lowerBound = ("1" + "0".repeat(originalValue.length - 1)).toLong()
            val upperBound = ("9".repeat(originalValue.length)).toLong()
            newId = random.longs(lowerBound, upperBound + 1).findAny().asLong
            idIsUnique =
                numericIdRepo.getByTerserAndReplacedValue(
                    normalizedTerserPath,
                    newId.toString()
                ) == null && newId != originalLong
        }
        return newId!!
    }

    fun getPseudonymIdForTerser(terserPath: String, originalValue: String): PseudonymId? {
        val normalizedTerser = PseudonymId.normalizeTerser(pseudonymizationRules, terserPath)
        val fromRepo = numericIdRepo.getByTerserAndOriginalValue(normalizedTerser, originalValue)
        return if (fromRepo == null) {
            val newId = when (val originalLong = originalValue.toLongOrNull()) {
                null -> UUID.randomUUID().toString()
                else -> generateLongPseudonym(normalizedTerser, originalValue, originalLong)
            }
            numericIdRepo.save(
                PseudonymId(
                    terser = normalizedTerser,
                    originalValue = originalValue,
                    replacedValue = newId.toString()
                )
            )

        } else
            fromRepo
    }
}