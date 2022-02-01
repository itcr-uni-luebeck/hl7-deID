package de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.repo

import org.hibernate.Hibernate
import org.springframework.data.repository.CrudRepository
import java.time.Duration
import java.time.LocalDate
import javax.persistence.*

@Entity
data class PatientPseudonym(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @OneToOne
    val identity: PatientIdentity? = null,
    val lastName: String,
    val firstName: String,
    val dateOfBirth: LocalDate?,
    private val dateTimeOffset: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as PatientPseudonym

        return id != null && id == other.id
    }

    override fun hashCode(): Int = 1994813688

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(id = $id , identity = $identity , lastName = $lastName , firstName = $firstName , dateOfBirth = $dateOfBirth )"
    }

    val offset: Duration get() = Duration.parse(dateTimeOffset)
}

interface PatientPseudonymRepo : CrudRepository<PatientPseudonym, Long> {
    fun getByIdentity(identity: PatientIdentity): PatientPseudonym?
}
