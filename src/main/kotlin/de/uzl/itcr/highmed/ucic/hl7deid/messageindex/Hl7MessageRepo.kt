package de.uzl.itcr.highmed.ucic.hl7deid.messageindex

import de.uzl.itcr.highmed.ucic.hl7deid.jpasearch.BaseRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface Hl7MessageRepoGenerated : CrudRepository<Hl7MessageEntity, String>,
    JpaSpecificationExecutor<Hl7MessageEntity> {

    @Query("SELECT DISTINCT triggerEvent from Hl7MessageEntity")
    fun getAvailableTriggers(): List<String>

    @Query("SELECT DISTINCT messageStructure from Hl7MessageEntity")
    fun getAvailableMsgStructures(): List<String>

    @Query("SELECT DISTINCT messageType from Hl7MessageEntity")
    fun getAvailableMsgTypes(): List<String>
}

@Repository
class Hl7MessageRepo(
    @Autowired private val generated: Hl7MessageRepoGenerated
) : BaseRepository<Hl7MessageEntity>() {

    override val resourceClass: Class<Hl7MessageEntity> get() = Hl7MessageEntity::class.java

    fun getAvailableTriggers() = generated.getAvailableTriggers()

    fun getAvailableMsgStructures() = generated.getAvailableMsgStructures()

    fun getAvailableMsgTypes() = generated.getAvailableMsgTypes()

    fun findById(messageId: String) = generated.findById(messageId)

    fun findByParameters(searchParameters: SearchParameters): List<Hl7MessageEntity> =
        criteriaQuery(
            listOf(
                Criterion(Hl7MessageEntity::messageType, searchParameters.msgType),
                Criterion(Hl7MessageEntity::triggerEvent, searchParameters.msgTrigger),
                Criterion(Hl7MessageEntity::messageStructure, searchParameters.msgStructure, SortOrder.IGNORE),
                Criterion(Hl7MessageEntity::patientId, searchParameters.pid),
                Criterion(Hl7MessageEntity::caseId, searchParameters.caseId),
                Criterion(Hl7MessageEntity::messageId, searchParameters.msgControlId),
            )
        )

    fun save(hl7MessageEntity: Hl7MessageEntity) = generated.save(hl7MessageEntity)
    fun countAll() = generated.count()

    data class SearchParameters(
        val msgType: String?,
        val msgTrigger: String?,
        val msgStructure: String?,
        val msgControlId: String?,
        val pid: String?,
        val caseId: String?
    )
}