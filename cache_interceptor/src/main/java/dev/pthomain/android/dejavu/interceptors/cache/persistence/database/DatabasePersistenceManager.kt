/*
 *
 *  Copyright (C) 2017-2020 Pierre Thomain
 *
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package dev.pthomain.android.dejavu.interceptors.cache.persistence.database

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.pthomain.android.boilerplate.core.utils.io.useAndLogError
import dev.pthomain.android.boilerplate.core.utils.kotlin.ifElse
import dev.pthomain.android.dejavu.configuration.DejaVuConfiguration
import dev.pthomain.android.dejavu.interceptors.cache.instruction.HashedRequestMetadata
import dev.pthomain.android.dejavu.interceptors.cache.instruction.ValidRequestMetadata
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Local.Clear
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Local.Invalidate
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Remote.Cache
import dev.pthomain.android.dejavu.interceptors.cache.persistence.PersistenceManager
import dev.pthomain.android.dejavu.interceptors.cache.persistence.base.BasePersistenceManager
import dev.pthomain.android.dejavu.interceptors.cache.persistence.base.CacheDataHolder
import dev.pthomain.android.dejavu.interceptors.cache.persistence.database.SqlOpenHelperCallback.Companion.COLUMNS.*
import dev.pthomain.android.dejavu.interceptors.cache.persistence.database.SqlOpenHelperCallback.Companion.TABLE_DEJA_VU
import dev.pthomain.android.dejavu.interceptors.cache.serialisation.SerialisationException
import dev.pthomain.android.dejavu.interceptors.cache.serialisation.SerialisationManager
import dev.pthomain.android.dejavu.interceptors.cache.serialisation.SerialisationManager.Factory.Type.DATABASE
import dev.pthomain.android.dejavu.interceptors.response.Response
import dev.pthomain.android.glitchy.interceptor.error.NetworkErrorPredicate
import io.requery.android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import java.util.*

/**
 * Provides a PersistenceManager implementation saving the responses to a SQLite database.
 *
 * @param database the opened database
 * @param serialisationManager used for the serialisation/deserialisation of the cache entries
 * @param dejaVuConfiguration the global cache configuration
 * @param dateFactory class providing the time, for the purpose of testing
 * @param contentValuesFactory converter from Map to ContentValues for testing purpose
 */
class DatabasePersistenceManager<E> internal constructor(
        private val database: SupportSQLiteDatabase,
        serialisationManager: SerialisationManager<E>,
        dejaVuConfiguration: DejaVuConfiguration<E>,
        dateFactory: (Long?) -> Date,
        private val contentValuesFactory: (Map<String, *>) -> ContentValues
) : BasePersistenceManager<E>(
        dejaVuConfiguration,
        serialisationManager,
        dateFactory
) where E : Throwable,
        E : NetworkErrorPredicate {

    /**
     * Clears the entries of a certain type as passed by the typeToClear argument (or all entries otherwise).
     * Both parameters work in conjunction to form an intersection of entries to be cleared.
     *
     * @param operation the Clear operation
     * @param requestMetadata the request's metadata
     * @throws SerialisationException in case the deserialisation failed
     */
    @Throws(SerialisationException::class)
    override fun <R> clearCache(operation: Clear,
                                requestMetadata: ValidRequestMetadata<R>) {
        val olderEntriesClause = ifElse(
                operation.clearStaleEntriesOnly,
                "${EXPIRY_DATE.columnName} < ?",
                null
        )

        val typeClause = "${CLASS.columnName} = ?"

        val args = arrayListOf<String>().apply {
            if (operation.clearStaleEntriesOnly) add(dateFactory(null).time.toString())
            add(requestMetadata.classHash) //TODO requestHash
        }

        database.delete(
                TABLE_DEJA_VU,
                arrayOf(olderEntriesClause, typeClause).filterNotNull().joinToString(separator = " AND "),
                args.toArray()
        ).let { deleted ->
            val entryType = requestMetadata.responseClass.simpleName
            if (operation.clearStaleEntriesOnly) {
                logger.d(this, "Deleted old $entryType entries from cache: $deleted found")
            } else {
                logger.d(this, "Deleted all existing $entryType entries from cache: $deleted found")
            }
        }
    }

    /**
     * Returns the cached data as a CacheDataHolder object.
     *
     * @param requestMetadata the associated request metadata
     *
     * @return the cached data as a CacheDataHolder
     * @throws SerialisationException in case the deserialisation failed
     */
    @Throws(SerialisationException::class)
    override fun <R> getCacheDataHolder(requestMetadata: HashedRequestMetadata<R>): CacheDataHolder? {
        val projection = arrayOf(
                DATE.columnName,
                EXPIRY_DATE.columnName,
                DATA.columnName,
                IS_COMPRESSED.columnName,
                IS_ENCRYPTED.columnName,
                CLASS.columnName
        )

        val query = """
            SELECT ${projection.joinToString(", ")}
            FROM $TABLE_DEJA_VU
            WHERE ${TOKEN.columnName} = '${requestMetadata.requestHash}'
            LIMIT 1
            """

        database.query(query)
                .useAndLogError(
                        {
                            with(it) {
                                val simpleName = requestMetadata.responseClass.simpleName
                                if (count != 0 && moveToNext()) {
                                    logger.d(this, "Found a cached $simpleName")

                                    val cacheDate = dateFactory(getLong(getColumnIndex(DATE.columnName)))
                                    val localData = getBlob(getColumnIndex(DATA.columnName))
                                    val isCompressed = getInt(getColumnIndex(IS_COMPRESSED.columnName)) != 0
                                    val isEncrypted = getInt(getColumnIndex(IS_ENCRYPTED.columnName)) != 0
                                    val expiryDate = dateFactory(getLong(getColumnIndex(EXPIRY_DATE.columnName)))
                                    val responseClassHash = getString(getColumnIndex(CLASS.columnName))
                                    //TODO verify the class hash is the same as the one the request metadata

                                    return CacheDataHolder.Complete(
                                            requestMetadata,
                                            cacheDate.time,
                                            expiryDate.time,
                                            localData,
                                            isCompressed,
                                            isEncrypted
                                    )
                                } else {
                                    logger.d(this, "Found no cached $simpleName")
                                    return null
                                }
                            }
                        },
                        logger
                )
    }

    /**
     * Invalidates the cached data (by setting the expiry date in the past, making the data STALE)
     * for entries past their expiry date.
     *
     * @param requestMetadata the request's metadata
     *
     * @return a Boolean indicating whether the data marked for invalidation was found or not
     */
    override fun <R> invalidateEntriesIfStale(requestMetadata: ValidRequestMetadata<R>): Boolean {
        return false //TODO
    }

    /**
     * Invalidates the cached data (by setting the expiry date in the past, making the data STALE).
     *
     * @param operation the request's Invalidate operation
     * @param requestMetadata the request's metadata
     *
     * @return a Boolean indicating whether the data marked for invalidation was found or not
     */
    override fun <R> forceInvalidation(operation: Invalidate,
                                       requestMetadata: ValidRequestMetadata<R>): Boolean {
        val map = mapOf(EXPIRY_DATE.columnName to 0)
        val selection = "${TOKEN.columnName} = ?"
        val selectionArgs = arrayOf(requestMetadata.requestHash) //TODO classHash

        val results = database.update(
                TABLE_DEJA_VU,
                CONFLICT_REPLACE,
                contentValuesFactory(map),
                selection,
                selectionArgs
        )

        val foundIt = results > 0

        logger.d(
                this,
                "Invalidating cache for ${requestMetadata.responseClass.simpleName}: ${if (foundIt) "done" else "nothing found"}"
        )

        return foundIt
    }

    /**
     * Caches a given response.
     *
     * @param responseWrapper the response to cache
     * @throws SerialisationException in case the serialisation failed
     */
    @Throws(SerialisationException::class)
    override fun <R : Any> cache(responseWrapper: Response<R, Cache>) {
        with(serialise(responseWrapper)) {
            val values = HashMap<String, Any>()
            values[TOKEN.columnName] = requestMetadata.requestHash
            values[DATE.columnName] = cacheDate
            values[EXPIRY_DATE.columnName] = expiryDate
            values[DATA.columnName] = data
            values[CLASS.columnName] = requestMetadata.classHash
            values[IS_COMPRESSED.columnName] = ifElse(isCompressed, 1, 0)
            values[IS_ENCRYPTED.columnName] = ifElse(isEncrypted, 1, 0)

            try {
                database.insert(
                        TABLE_DEJA_VU,
                        CONFLICT_REPLACE,
                        contentValuesFactory(values)
                )
            } catch (e: Exception) {
                throw SerialisationException("Could not save the response to database", e)
            }
        }
    }

    //TODO remove this
    class Factory<E> internal constructor(private val database: SupportSQLiteDatabase,
                                          private val serialisationManagerFactory: SerialisationManager.Factory<E>,
                                          private val dejaVuConfiguration: DejaVuConfiguration<E>,
                                          private val dateFactory: (Long?) -> Date,
                                          private val contentValuesFactory: (Map<String, *>) -> ContentValues)
            where E : Throwable,
                  E : NetworkErrorPredicate {

        fun create(): PersistenceManager<E> = DatabasePersistenceManager(
                database,
                serialisationManagerFactory.create(DATABASE),
                dejaVuConfiguration,
                dateFactory,
                contentValuesFactory
        )
    }

}
