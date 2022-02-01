package de.uzl.itcr.highmed.ucic.hl7deid.messageindex

import org.hibernate.Hibernate
import javax.persistence.*

@Entity
@Table(
    indexes = [
        Index(columnList = "patientId"),
        Index(columnList = "caseId"),
        Index(columnList = "messageType"),
        Index(columnList = "triggerEvent"),
        Index(columnList = "messageStructure")]
)
data class Hl7MessageEntity(
    @Id
    val messageId: String,
    // @Enumerated(EnumType.STRING)
    val messageType: String,
    val triggerEvent: String,
    val messageStructure: String?,
    val patientId: String?,
    val caseId: String?,
    val filename: String?
) {
    enum class MessageType {
        ADT, ORU
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as Hl7MessageEntity

        return messageId == other.messageId
    }

    override fun hashCode(): Int = 887303802

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(messageId = $messageId , messageType = $messageType , triggerEvent = $triggerEvent , messageStructure = $messageStructure , patientId = $patientId , caseId = $caseId , filename = $filename )"
    }
}
