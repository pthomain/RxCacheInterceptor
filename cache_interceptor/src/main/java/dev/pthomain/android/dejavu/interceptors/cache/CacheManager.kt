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

package dev.pthomain.android.dejavu.interceptors.cache

import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Local.Clear
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Local.Invalidate
import dev.pthomain.android.dejavu.interceptors.cache.instruction.operation.Operation.Remote.Cache
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.CacheStatus.STALE
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.InstructionToken
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.RequestToken
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.ResponseToken
import dev.pthomain.android.dejavu.interceptors.cache.persistence.PersistenceManager
import dev.pthomain.android.dejavu.interceptors.error.ResponseWrapper
import dev.pthomain.android.dejavu.interceptors.error.newMetadata
import dev.pthomain.android.dejavu.interceptors.response.EmptyResponseWrapperFactory
import dev.pthomain.android.dejavu.interceptors.response.Response
import dev.pthomain.android.glitchy.interceptor.error.ErrorFactory
import dev.pthomain.android.glitchy.interceptor.error.NetworkErrorPredicate
import io.reactivex.Observable
import java.util.*

/**
 * Handles the Observable composition according to the each cache operation.
 *
 * @param persistenceManager handles the persistence of the cached responses
 * @param cacheMetadataManager handles the update of the ResponseWrapper metadata
 * @param emptyResponseWrapperFactory handles the creation of empty ResponseWrappers for cases where no data can be returned
 * @param dateFactory converts timestamps to Dates
 * @param logger a Logger instance
 */
internal class CacheManager<E>(
        private val persistenceManager: PersistenceManager<E>,
        private val cacheMetadataManager: CacheMetadataManager<E>,
        private val emptyResponseWrapperFactory: EmptyResponseWrapperFactory<E>,
        private val errorFactory: ErrorFactory<E>,
        private val dateFactory: (Long?) -> Date,
        private val logger: Logger
) where E : Throwable,
        E : NetworkErrorPredicate {

    /**
     * Handles the CLEAR operation
     *
     * @param instructionToken the original request's instruction token
     *
     * @return an Observable emitting an empty ResponseWrapper (with a DONE status)
     */
    fun clearCache(instructionToken: InstructionToken<Clear>) =
            emptyResponseObservable(instructionToken, dateFactory(null).time) {
                with(instructionToken.instruction) {
                    persistenceManager.clearCache(operation, requestMetadata)
                }
            }

    /**
     * Handles the INVALIDATE operation
     *
     * @param instructionToken the original request's instruction token
     *
     * @return an Observable emitting an empty ResponseWrapper (with a DONE status)
     */
    fun invalidate(instructionToken: InstructionToken<Invalidate>) =
            emptyResponseObservable(instructionToken, dateFactory(null).time) {
                with(instructionToken.instruction) {
                    persistenceManager.forceInvalidation(operation, requestMetadata)
                }
            }

    /**
     * Wraps a callable action into an Observable that only emits an empty ResponseWrapper (with a DONE status).
     *
     * @param instructionToken the original request's instruction token
     * @param action the callable action to execute as an Observable
     *
     * @return an Observable emitting an empty ResponseWrapper (with a DONE status)
     */
    private fun <O : Operation, T : InstructionToken<O>> emptyResponseObservable(instructionToken: T,
                                                                                 start: Long,
                                                                                 action: () -> Unit = {}) =
            Observable.fromCallable(action::invoke).map {
                emptyResponseWrapperFactory.create(
                        instructionToken,
                        dateFactory(start).time
                )
            }!!

    /**
     * Handles any operation extending of the Expiring type.
     *
     * @param upstream the Observable being composed, typically created by Retrofit and composed by an ErrorInterceptor
     * @see dev.pthomain.android.dejavu.interceptors.error.ErrorInterceptor
     * @param instructionToken the original request's instruction token
     * @param start the time at which the request was made
     *
     * @return an Observable emitting an empty ResponseWrapper (with a DONE status)
     */
    fun getCachedResponse(upstream: Observable<ResponseWrapper<Cache, RequestToken<Cache>, E>>,
                          instructionToken: InstructionToken<Cache>,
                          start: Long) =
            Observable.defer<ResponseWrapper<Cache, RequestToken<Cache>, E>> {
                val cacheOperation = instructionToken.instruction.operation

                val instruction = instructionToken.instruction
                val mode = cacheOperation.priority.network
                val simpleName = instruction.requestMetadata.responseClass.simpleName

                logger.d(this, "Checking for cached $simpleName")
                val cachedResponse = persistenceManager.getCachedResponse(instructionToken)

                val responseWrapper = if (cachedResponse != null) {
                    logger.d(
                            this,
                            "Found cached $simpleName, status: ${cachedResponse.cacheToken.status}"
                    )
                    ResponseWrapper<Cache, RequestToken<Cache>, E>(
                            cachedResponse,
                            errorFactory.newMetadata(
                                    with(cachedResponse.cacheToken) {
                                        ResponseToken(instruction, status, requestDate)
                                    },
                                    null
                            )
                    )
                } else null

                val diskDuration = (dateFactory(null).time - start).toInt()

                if (mode.isLocalOnly()) {
                    if (responseWrapper == null)
                        emptyResponseObservable(instructionToken, start)
                    else
                        Observable.just(responseWrapper)
                } else
                    getOnlineObservable(
                            responseWrapper,
                            upstream,
                            cacheOperation,
                            instructionToken,
                            diskDuration
                    )
            }

    //TODO JavaDoc
    private fun getOnlineObservable(cachedResponse: ResponseWrapper<Cache, RequestToken<Cache>, E>?,
                                    upstream: Observable<ResponseWrapper<Cache, RequestToken<Cache>, E>>,
                                    cacheOperation: Cache,
                                    instructionToken: InstructionToken<Cache>,
                                    diskDuration: Int) =
            Observable.defer<ResponseWrapper<Cache, RequestToken<Cache>, E>> {
                val cachedResponseToken = cachedResponse?.metadata?.cacheToken
                val status = cachedResponseToken?.status
                val simpleName = instructionToken.instruction.requestMetadata.responseClass.simpleName

                if (cachedResponse == null || status == STALE) {
                    val fetchAndCache = fetchAndCache(
                            cachedResponse,
                            upstream,
                            cacheOperation,
                            instructionToken,
                            diskDuration
                    )

                    if (status == STALE && cacheOperation.priority.freshness.emitsCachedStale) {
                        Observable.concat(
                                Observable.just(cachedResponse).doOnNext {
                                    logger.d(this, "Delivering cached $simpleName, status: $status")
                                },
                                fetchAndCache
                        )
                    } else fetchAndCache
                } else Observable.just(cachedResponse)
            }

    private fun fetchAndCache(previousCachedResponse: ResponseWrapper<Cache, RequestToken<Cache>, E>?,
                              upstream: Observable<ResponseWrapper<Cache, RequestToken<Cache>, E>>,
                              cacheOperation: Cache,
                              instructionToken: InstructionToken<Cache>,
                              diskDuration: Int) =
            Observable.defer<ResponseWrapper<Cache, RequestToken<Cache>, E>> {
                val simpleName = instructionToken.instruction.requestMetadata.responseClass.simpleName
                logger.d(this, "$simpleName is STALE, attempting to refresh")

                upstream
                        .map {
                            cacheMetadataManager.setNetworkCallMetadata(
                                    it,
                                    cacheOperation,
                                    previousCachedResponse,
                                    instructionToken,
                                    diskDuration
                            )
                        }
                        .map { wrapper: ResponseWrapper<Cache, RequestToken<Cache>, E> ->
                            if (wrapper.metadata.exception != null) {
                                logger.e(
                                        this,
                                        wrapper.metadata.exception!!,
                                        "An error occurred fetching $simpleName"
                                )
                                wrapper
                            } else {
                                logger.d(this, "Finished fetching $simpleName, now caching")
                                try {
                                    persistenceManager.cache(Response(
                                            wrapper.response!!,
                                            with(wrapper.metadata.cacheToken) {
                                                ResponseToken(instruction, status, requestDate) //TODO check expiry date etc
                                            },
                                            wrapper.metadata.callDuration
                                    ))
                                } catch (e: Exception) {
                                    return@map cacheMetadataManager.setSerialisationFailedMetadata(
                                            wrapper,
                                            e
                                    )
                                }

                                logger.d(this, "Finished caching $simpleName, now delivering")
                                wrapper
                            }
                        }
            }

}

