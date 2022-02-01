package de.uzl.itcr.highmed.ucic.hl7analysis.pseudonym.repo

import org.hibernate.Hibernate
import org.springframework.data.repository.CrudRepository
import java.time.Duration
import java.time.LocalDate
import javax.persistence.*

@Entity
data class PatientIdentity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @ElementCollection
    val patientIdList: List<String>,
    val lastName: String?,
    val firstName: String?,
    @Enumerated(EnumType.STRING)
    val administrativeSex: AdministrativeSex?,
    val dateOfBirth: LocalDate?
) {

    @Suppress("unused")
    enum class AdministrativeSex {
        MALE, FEMALE, OTHER
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as PatientIdentity

        return id != null && id == other.id
    }

    override fun hashCode(): Int = 1764861151

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(id = $id , patientIdList = $patientIdList , lastName = $lastName , firstName = $firstName , administrativeSex = $administrativeSex , dateOfBirth = $dateOfBirth )"
    }
}

interface PatientIdentityRepo : CrudRepository<PatientIdentity, Long> {
    fun getByPatientIdList(patientId: String): PatientIdentity?
}
