package de.uzl.itcr.highmed.ucic.hl7deid.pseudonym.repo

import org.hibernate.Hibernate
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import javax.persistence.Entity
import javax.persistence.Id

@Entity
data class MessageId(
    @Id val messageControlId: String,
    val pseudoId: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as MessageId

        return messageControlId == other.messageControlId
    }

    override fun hashCode(): Int = 1455421054

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(messageControlId = $messageControlId , pseudoId = $pseudoId )"
    }
}

interface MessageIdRepo : CrudRepository<MessageId, String> {
    fun getByMessageControlId(messageControlId: String): MessageId?
    fun getByPseudoId(pseudoId: String): MessageId?
}
