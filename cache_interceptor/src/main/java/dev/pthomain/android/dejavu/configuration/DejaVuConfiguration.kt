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

package dev.pthomain.android.dejavu.configuration

import android.annotation.SuppressLint
import android.content.Context
import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.dejavu.DejaVu
import dev.pthomain.android.dejavu.injection.DejaVuComponent
import dev.pthomain.android.dejavu.interceptors.cache.TransientResponse
import dev.pthomain.android.dejavu.interceptors.cache.instruction.RequestMetadata
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.CachePriority.STALE_ACCEPTED_FIRST
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Remote
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Remote.Cache
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Remote.DoNotCache
import dev.pthomain.android.dejavu.interceptors.cache.persistence.PersistenceManager
import dev.pthomain.android.dejavu.interceptors.cache.persistence.PersistenceManagerFactory
import dev.pthomain.android.glitchy.interceptor.error.ErrorFactory
import dev.pthomain.android.glitchy.interceptor.error.NetworkErrorPredicate
import dev.pthomain.android.mumbo.Mumbo
import dev.pthomain.android.mumbo.base.EncryptionManager

/**
 * Class holding the global cache configuration. Values defined here are used by default
 * but (for some of them) can be overridden on a per-call basis via the use of arguments
 * passed to the call's CacheInstruction (and by extension in the Retrofit call's annotation).
 *
 * @see dev.pthomain.android.dejavu.interceptors.cache.instruction.CacheInstruction
 * @see dev.pthomain.android.dejavu.retrofit.annotations.Cache
 * @see dev.pthomain.android.dejavu.retrofit.annotations.DoNotCache
 * @see dev.pthomain.android.dejavu.retrofit.annotations.Invalidate
 * @see dev.pthomain.android.dejavu.retrofit.annotations.Clear
 *
 * TODO update JavaDoc
 */
class DejaVuConfiguration<E> internal constructor(
        internal val context: Context,
        internal val logger: Logger,
        internal val errorFactory: ErrorFactory<E>,
        internal val serialiser: Serialiser,
        internal val encryptionManager: EncryptionManager?,
        internal val useDatabase: Boolean,
        internal val persistenceManagerPicker: ((PersistenceManagerFactory<E>) -> PersistenceManager<E>)?,
        internal val operationPredicate: (metadata: RequestMetadata<*>) -> Remote?,
        internal val durationPredicate: (TransientResponse<*>) -> Int?
) where E : Throwable,
        E : NetworkErrorPredicate {

    companion object {
        const val DEFAULT_CACHE_DURATION_IN_SECONDS = 3600 //1h

        sealed class CachePredicate(private val operation: Remote?)
            : (RequestMetadata<*>) -> Remote? {

            override fun invoke(requestMetadata: RequestMetadata<*>) = operation

            object Inactive : CachePredicate(null)
            object CacheNone : CachePredicate(DoNotCache)
            object CacheAll : CachePredicate(Cache(STALE_ACCEPTED_FIRST, DEFAULT_CACHE_DURATION_IN_SECONDS))
        }

    }

    class Builder<E> internal constructor(
            private val errorFactory: ErrorFactory<E>,
            private val context: Context,
            private val serialiser: Serialiser,
            private val componentProvider: (DejaVuConfiguration<E>) -> DejaVuComponent<E>
    ) where E : Throwable,
            E : NetworkErrorPredicate {

        private var logger: Logger = object : Logger {
            override fun d(tagOrCaller: Any, message: String) = Unit
            override fun e(tagOrCaller: Any, message: String) = Unit
            override fun e(tagOrCaller: Any, t: Throwable, message: String?) = Unit
        }
        private var customErrorFactory: ErrorFactory<E>? = null
        private var mumboPicker: ((Mumbo) -> EncryptionManager)? = null
        private var useDatabase = false

        private val defaultPersistenceManagerPicker: ((PersistenceManagerFactory<E>) -> PersistenceManager<E>) = {
            it.filePersistenceManagerFactory.create()
        }
        private var persistenceManagerPicker = defaultPersistenceManagerPicker

        private var operationPredicate: (RequestMetadata<*>) -> Remote? = CachePredicate.Inactive
        private var durationPredicate: (TransientResponse<*>) -> Int? = { null }

        /**
         * Sets custom logger.
         */
        fun withLogger(logger: Logger) =
                apply { this.logger = logger }

        /**
         * Sets a custom ErrorFactory implementation.
         */
        fun withErrorFactory(errorFactory: ErrorFactory<E>) =
                apply { this.customErrorFactory = errorFactory }

        /**
         * Sets the EncryptionManager implementation. Can be used to provide a custom implementation
         * or to choose one provided by the Mumbo library. For compatibility reasons, the default is
         * Facebook Conceal, but apps targeting API 23+ should use Tink (JetPack).
         *
         * @param mumboPicker picker for the encryption implementation, with a choice of:
         * - Facebook's Conceal for API levels < 23 (see https://facebook.github.io/conceal)
         * - AndroidX's JetPack Security (Tink) implementation for API level >= 23 only (see https://developer.android.com/jetpack/androidx/releases/security)
         * - custom implementation using the EncryptionManager interface
         *
         * NB: if you are targeting API level 23 or above, you should use Tink as it is a more secure implementation.
         * However if your API level target is less than 23, using Tink will trigger a runtime exception.
         */
        fun withEncryption(mumboPicker: (Mumbo) -> EncryptionManager) =
                apply { this.mumboPicker = mumboPicker }

        private fun defaultEncryptionManager(mumbo: Mumbo,
                                             context: Context) = with(mumbo) {
            with(context.packageManager.getApplicationInfo(
                    context.packageName,
                    0
            )) {
                @SuppressLint("NewApi")
                if (targetSdkVersion >= 23) tink()
                else conceal()
            }
        }

        /**
         * Provide a different PersistenceManager to handle the persistence of the cached requests.
         * The default implementation is FilePersistenceManager which saves the responses to
         * the filesystem.
         *
         * @see dev.pthomain.android.dejavu.interceptors.cache.persistence.base.KeyValuePersistenceManager
         * @see dev.pthomain.android.dejavu.interceptors.cache.persistence.database.DatabasePersistenceManager
         *
         * @param enableDatabase whether or not to instantiate the classes related to database caching (this will give access to DatabasePersistenceManager.Factory)
         * @param persistenceManagerPicker a picker providing a PersistenceManagerFactory and returning the PersistenceManager implementation to override the default one
         */
        fun withPersistence(enableDatabase: Boolean,
                            persistenceManagerPicker: (PersistenceManagerFactory<E>) -> PersistenceManager<E>) =
                apply {
                    useDatabase = enableDatabase
                    this.persistenceManagerPicker = persistenceManagerPicker
                }

        /**
         * Sets a predicate for ad-hoc response caching. This predicate will be called for every
         * request and will always take precedence on the provided request operation.
         *
         * It will be called with the target response class and associated request metadata before
         * the call is made in order to establish the operation to associate with that request.
         * @see Remote //TODO explain remote vs local
         *
         * Returning null means the cache will instead take into account the directives defined
         * as annotation or header on that request (if present).
         *
         * To cache all response with default CACHE operation and expiration period, use CachePredicate.CacheAll.
         * To disable any caching, use CachePredicate.CacheNone.
         *
         * Otherwise, you can implement your own predicate to return the appropriate operation base on
         * the given RequestMetadata for the request being made.
         *///TODO rename to Provider
        fun withOperationPredicate(operationPredicate: (metadata: RequestMetadata<*>) -> Remote?) =
                apply { this.operationPredicate = operationPredicate }

        /**
         * Sets a predicate for ad-hoc response duration caching.
         *
         * If not null, the value returned by this predicate will take precedence on the
         * cache duration provided in the request's operation.
         *
         * This is useful for responses containing cache duration information, enabling server-side
         * cache control.
         *///TODO rename to Provider
        fun withDurationPredicate(durationPredicate: (TransientResponse<*>) -> Int?) =
                apply { this.durationPredicate = durationPredicate }

        /**
         * Returns an instance of DejaVu.
         */
        fun build(): DejaVu<E> {
            val encryptionManager = with(Mumbo(context, logger)) {
                try {
                    mumboPicker?.invoke(this)
                            ?: defaultEncryptionManager(this, context)
                } catch (exception: Exception) {
                    null
                }
            }

            return DejaVu(
                    componentProvider(
                            DejaVuConfiguration(
                                    context.applicationContext,
                                    logger,
                                    customErrorFactory ?: errorFactory,
                                    serialiser,
                                    encryptionManager,
                                    useDatabase,
                                    persistenceManagerPicker,
                                    operationPredicate,
                                    durationPredicate
                            ).also { logger.d(this, "DejaVu set up with the following configuration: $it") }
                    )
            )
        }
    }

}