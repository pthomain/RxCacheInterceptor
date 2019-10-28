/*
 *
 *  Copyright (C) 2017 Pierre Thomain
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

package dev.pthomain.android.dejavu.configuration.instruction

import dev.pthomain.android.dejavu.configuration.instruction.Operation.Type.*

/**
 * Represent a cache operation. Directives defined here take precedence over global config.
 */
sealed class Operation(val type: Type) {

    /**
     * Expiring instructions contain a durationInMillis indicating the duration of the cached value
     * in milliseconds.
     *
     * @param durationInMillis duration of the cache for this specific call in milliseconds, during which the data is considered FRESH
     * @param connectivityTimeoutInMillis maximum time to wait for the network connectivity to become available to return an online response (does not apply to cached responses)
     * @param freshOnly whether or not the operation allows STALE data to be returned from the cache
     * @param mergeOnNextOnError allows exceptions to be intercepted and treated as an empty response metadata and delivered as such via onNext. Only used if the the response implements CacheMetadata.Holder. An exception is thrown otherwise.
     * @param encrypt whether the cached data should be encrypted, useful for use on external storage
     * @param compress whether the cached data should be compressed, useful for large responses
     * @param filterFinal whether this operation should return data in a transient state (i.e. STALE and awaiting refresh). Singles will always return final data unless the global allowNonFinalForSingle directive is set to true.
     * @param type the operation type
     */
    //TODO replace flags with CachePriority and merge all Expiring operations in one (rename to Get)
    sealed class Expiring(open val durationInMillis: Long?,
                          open val connectivityTimeoutInMillis: Long?,
                          @Deprecated("Replace with CachePriority")
                          open val freshOnly: Boolean,
                          @Deprecated("This adds unnecessary complexity")
                          open val mergeOnNextOnError: Boolean?,
                          open val encrypt: Boolean?,
                          open val compress: Boolean?,
                          @Deprecated("Replace with CachePriority")
                          open val filterFinal: Boolean,
                          type: Type) : Operation(type) {

        /**
         * CACHE instructions are the default ones. They will fetch data from the network if no FRESH data
         * is found in the cache. Otherwise, cache data is returned and no network call is attempted.
         * If no FRESH data is found locally a network call is made and the result is returned then cached
         * for the duration defined by durationInMillis.
         *
         * This instruction is overridden by the cachePredicate. TODO check this, cache predicate should take precedence
         * @see dev.pthomain.android.dejavu.configuration.DejaVuConfiguration.cachePredicate
         *
         * @param durationInMillis duration of the cache for this specific call in milliseconds, during which the data is considered FRESH
         * @param connectivityTimeoutInMillis maximum time to wait for the network connectivity to become available to return an online response (does not apply to cached responses)
         * @param freshOnly whether or not the operation allows STALE data to be returned from the cache
         * @param mergeOnNextOnError allows exceptions to be intercepted and treated as an empty response metadata and delivered as such via onNext. Only used if the the response implements CacheMetadata.Holder. An exception is thrown otherwise.
         * @param encrypt whether the cached data should be encrypted, useful for use on external storage
         * @param compress whether the cached data should be compressed, useful for large responses
         * @param filterFinal whether this operation should return data in a transient state (i.e. STALE and awaiting refresh). Singles will always return final data unless the global allowNonFinalForSingle directive is set to true.
         */
        data class Cache(override val durationInMillis: Long? = null,
                         override val connectivityTimeoutInMillis: Long? = null,
                         override val freshOnly: Boolean = false,
                         override val mergeOnNextOnError: Boolean? = null,
                         override val encrypt: Boolean? = null,
                         override val compress: Boolean? = null,
                         override val filterFinal: Boolean = false)
            : Expiring(
                durationInMillis,
                connectivityTimeoutInMillis,
                freshOnly,
                mergeOnNextOnError,
                encrypt,
                compress,
                filterFinal,
                CACHE
        ) {
            override fun toString() = super.toString()
        }

        /**
         * REFRESH instructions will invalidate the data currently cached for the call
         * and force a refresh even though the data might still be considered FRESH.
         * Once invalidated through a REFRESH call, the data is considered permanently STALE
         * until REFRESHED. This is the equivalent of chaining INVALIDATE and CACHE.
         *
         * @param durationInMillis duration of the cache for this specific call in milliseconds, during which the data is considered FRESH
         * @param connectivityTimeoutInMillis maximum time to wait for the network connectivity to become available to return an online response (does not apply to cached responses)
         * @param freshOnly whether or not the operation allows STALE data to be returned from the cache
         * @param mergeOnNextOnError allows exceptions to be intercepted and treated as an empty response metadata and delivered as such via onNext. Only used if the the response implements CacheMetadata.Holder. An exception is thrown otherwise.
         * @param filterFinal whether this operation should return data in a transient state (i.e. STALE and awaiting refresh). Singles will always return final data unless the global allowNonFinalForSingle directive is set to true.
         *
         * @see Invalidate
         * */
        //FIXME merge this with CACHE and add a refresh parameter. This is to prevent conflicting values defined in both.
        data class Refresh(override val durationInMillis: Long? = null,
                           override val connectivityTimeoutInMillis: Long? = null,
                           override val freshOnly: Boolean = false,
                           override val mergeOnNextOnError: Boolean? = null,
                           override val filterFinal: Boolean = false)
            : Expiring(
                durationInMillis,
                connectivityTimeoutInMillis,
                freshOnly,
                mergeOnNextOnError,
                null,
                null,
                filterFinal,
                REFRESH
        ) {
            override fun toString() = super.toString()
        }

        /**
         * OFFLINE instructions will only return cached data if available or an empty response
         * if none is available. This call can return either FRESH or STALE data (if the freshOnly directive is not set).
         * No network call will ever be attempted.
         *
         * @param freshOnly whether or not the operation allows STALE data to be returned from the cache
         * @param mergeOnNextOnError allows exceptions to be intercepted and treated as an empty response metadata and delivered as such via onNext. Only used if the the response implements CacheMetadata.Holder. An exception is thrown otherwise.
         */
        data class Offline(override val freshOnly: Boolean = false,
                           override val mergeOnNextOnError: Boolean? = null)
            : Expiring(
                null,
                null,
                freshOnly,
                mergeOnNextOnError,
                null,
                null,
                false,
                OFFLINE
        ) {
            override fun toString() = super.toString()
        }

        override fun toString() = SERIALISER.serialise(
                type,
                durationInMillis,
                connectivityTimeoutInMillis,
                freshOnly,
                mergeOnNextOnError,
                encrypt,
                compress,
                filterFinal
        )

    }

    /**
     * DO_NOT_CACHE instructions are not attempting to cache the response. However, generic error handling
     * will still be applied.
     *
     * @see dev.pthomain.android.dejavu.configuration.error.ErrorFactory
     */
    object DoNotCache : Operation(DO_NOT_CACHE)

    /**
     * INVALIDATE instructions invalidate the currently cached data if present and do not return any data.
     * They should usually be used with a Completable. However, if used with a Single or Observable,
     * they will return an empty response with cache metadata (if the response implements CacheMetadata.Holder).
     *
     * @param useRequestParameters whether or not the request parameters should be used to identify the unique cached entry to invalidate
     */
    data class Invalidate(val useRequestParameters: Boolean = false) : Operation(INVALIDATE) {
        override fun toString() = SERIALISER.serialise(
                type,
                useRequestParameters
        )
    }

    /**
     * CLEAR instructions clear the cached data for this call if present and do not return any data.
     * They should usually be used with a Completable. However, if used with a Single or Observable,
     * they will return an empty response with cache metadata (if the response implements CacheMetadata.Holder).
     *
     * @param useRequestParameters whether or not the request parameters should be used to identify the unique cached entry to clear
     * @param clearStaleEntriesOnly whether or not to clear the STALE data only. When set to true, only expired data is cleared, otherwise STALE and FRESH data is cleared.
     */
    data class Clear(val useRequestParameters: Boolean = false,
                     val clearStaleEntriesOnly: Boolean = false) : Operation(CLEAR) {
        override fun toString() = SERIALISER.serialise(
                type,
                useRequestParameters,
                clearStaleEntriesOnly
        )
    }

    /**
     * Wipes clean the entire cache.
     * Should usually be used with a Completable. However, if used with a Single or Observable,
     * they will return an empty response with cache metadata (if the response implements CacheMetadata.Holder).
     */
    object Wipe : Operation(CLEAR) {
        override fun toString() = SERIALISER.serialise(type)
    }

    override fun toString() = SERIALISER.serialise(type)

    companion object {
        @JvmStatic
        private val SERIALISER = OperationSerialiser()

        fun fromString(string: String) = SERIALISER.deserialise(string)
    }

    /**
     * The operation's type.
     *
     * @param annotationName the associated annotation name.
     * @param isCompletable whether or not this operation returns data and as such can be used with a Completable.
     */
    enum class Type(val annotationName: String,
                    val isCompletable: Boolean = false) {
        DO_NOT_CACHE("@DoNotCache"),
        CACHE("@Cache"),
        REFRESH("@Refresh"),
        OFFLINE("@Offline"),
        INVALIDATE("@Invalidate", true),
        CLEAR("@Clear", true)
    }

}
