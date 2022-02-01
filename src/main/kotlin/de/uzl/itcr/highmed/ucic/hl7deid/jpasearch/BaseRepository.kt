package de.uzl.itcr.highmed.ucic.hl7analysis.jpasearch

import javax.persistence.EntityManager
import javax.persistence.PersistenceContext
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root
import kotlin.reflect.KProperty1

typealias CriteriaQueryBuilder<V, T> = (CriteriaBuilder.(query: CriteriaQuery<V>, entity: Root<T>) -> Unit)?

/**
 * abstract class for repositories that can be easily searched using the JPA Criteria API
 * @param T the type of this repository
 * @property entityManager EntityManager the entity manager associated with this repo
 * @property resourceClass Class<T> the resource class of this repo
 */
abstract class BaseRepository<T> {

    @PersistenceContext
    protected lateinit var entityManager: EntityManager

    abstract val resourceClass: Class<T>

    /**
     * build a criteria call from the provided criteria
     * @param clazz Class<V> the class th return
     * @param build [@kotlin.ExtensionFunctionType] Function3<CriteriaBuilder, [@kotlin.ParameterName] CriteriaQuery<V>, [@kotlin.ParameterName] Root<T>, Unit>? the chain builder function
     * @return TypedQuery<V> a query that can be called
     */
    private fun <V> criteria(
        clazz: Class<V>,
        build: CriteriaQueryBuilder<V, T> = null
    ): TypedQuery<V> {
        val builder: CriteriaBuilder = entityManager.criteriaBuilder
        val query: CriteriaQuery<V> = builder.createQuery(clazz)
        val entity: Root<T> = query.from(resourceClass)
        build?.invoke(builder, query, entity)
        return entityManager.createQuery(query)
    }

    /**
     * build a query using the encapsulated resource class (this returns a list of the same type as the resource class)
     * @param build [@kotlin.ExtensionFunctionType] Function3<CriteriaBuilder, [@kotlin.ParameterName] CriteriaQuery<T>, [@kotlin.ParameterName] Root<T>, Unit>? the builder
     * @return TypedQuery<T> a query for execution
     */
    private fun criteria(build: CriteriaQueryBuilder<T, T> = null) = criteria(resourceClass, build)

    /**
     * build a query from the provided criteria list. This is a map of maps, in effect, resolving fields to String values.
     * By providing the sort order, the query can be ordered on the DB.
     * @param criteriaList List<Criterion<T, V>> the list of criteria to use
     * @return List<T> the list of requls
     */
    protected fun <V> criteriaQuery(criteriaList: List<Criterion<T, V>>): List<T> =
        criteria { query, entity ->
            val whereClause = (criteriaList.mapNotNull { c ->
                c.stringValue?.valueOrNull()?.let {
                    equal(entity.get(c.property), it)
                }
            }).toTypedArray()
            query.where(*whereClause)
            val orderClause = criteriaList.mapNotNull { c ->
                when (c.sortOrder) {
                    SortOrder.ASC -> asc(entity.get(c.property))
                    SortOrder.DESC -> desc(entity.get(c.property))
                    SortOrder.IGNORE -> null
                }

            }.toTypedArray()
            query.orderBy(*orderClause)
        }.resultList

    /**
     * store a equals criterion for the criteria API, and associated sort order
     * @param T the type of receiver, i.e. the property class
     * @param V the type of the property value, i.e. String
     * @property property KProperty1<T, V> the property to match against
     * @property stringValue String? the (user-provided) value to match against
     * @property sortOrder SortOrder the sort order, can be Ignore
     */
    data class Criterion<T, V>(
        val property: KProperty1<T, V>,
        val stringValue: String?,
        val sortOrder: SortOrder = SortOrder.ASC
    )

    enum class SortOrder {
        ASC,
        DESC,
        IGNORE
    }
}

/**
 * get a property as a criteria path
 * @receiver Root<T> the root type in the from clause
 * @param prop KProperty1<T, V> the property to access
 * @return Path<V> the path of this property
 */
fun <T, V> Root<T>.get(prop: KProperty1<T, V>): Path<V> = this.get(prop.name)

/**
 * returns a null value if the string is empty
 * @receiver String? the value to clean
 * @return String? the original value, or null if empty
 */
fun String?.valueOrNull() = this?.ifEmpty { null }