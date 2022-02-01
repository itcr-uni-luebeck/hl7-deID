package de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.repo

import de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.model.PseudonymizationRules
import org.hibernate.Hibernate
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id

@Entity
data class PseudonymId(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val terser: String,
    val originalValue: String,
    val replacedValue: String
) {
    companion object {

        private val log: Logger = LoggerFactory.getLogger(PseudonymId::class.java)

        fun normalizeTerser(pseudonymizationRules: PseudonymizationRules, inTerser: String): String {
            val sanitizedTerser = inTerser.replace("""^/(\.)?""".toRegex(), "")
            val resolveChain: MutableList<String> = mutableListOf(sanitizedTerser)
            fun replaceTerserIteration(normalizations: List<PseudonymizationRules.NormalizedTerser>, terser: String) =
                normalizations.find { it.from == terser }?.let {
                    resolveChain.add(it.to)
                    return@let it.to
                } ?: terser

            var replacedTerser = sanitizedTerser
            pseudonymizationRules.normalizedTersers?.let {
                do {
                    val previousReplaced = replacedTerser
                    replacedTerser = replaceTerserIteration(pseudonymizationRules.normalizedTersers, replacedTerser)
                } while (replacedTerser != previousReplaced)
            }

            if (replacedTerser != sanitizedTerser) {
                log.debug("Resolved terser: ${resolveChain.joinToString("->")}")
            }
            return replacedTerser
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as PseudonymId

        return id != null && id == other.id
    }

    override fun hashCode(): Int = 183254157

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(id = $id , terser = $terser , originalValue = $originalValue , replacedValue = $replacedValue )"
    }
}

@Repository
interface NumericIdRepo : CrudRepository<PseudonymId, Long> {
    fun getByTerserAndOriginalValue(terser: String, originalValue: String): PseudonymId?
    fun getByTerserAndReplacedValue(terser: String, replacedValue: String): PseudonymId?
}

