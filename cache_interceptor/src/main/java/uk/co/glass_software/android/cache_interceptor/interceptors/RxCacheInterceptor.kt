/*
 * Copyright (C) 2017 Glass Software Ltd
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package uk.co.glass_software.android.cache_interceptor.interceptors

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import uk.co.glass_software.android.cache_interceptor.configuration.CacheConfiguration
import uk.co.glass_software.android.cache_interceptor.configuration.CacheInstruction
import uk.co.glass_software.android.cache_interceptor.configuration.CacheInstruction.Operation.DoNotCache
import uk.co.glass_software.android.cache_interceptor.configuration.CacheInstruction.Operation.Expiring
import uk.co.glass_software.android.cache_interceptor.configuration.NetworkErrorProvider
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.CacheInterceptor
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.token.CacheToken
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.token.CacheToken.Companion.fromInstruction
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.error.ErrorInterceptor
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.response.ResponseInterceptor

class RxCacheInterceptor<E> private constructor(instruction: CacheInstruction,
                                                url: String,
                                                uniqueParameters: String?,
                                                configuration: CacheConfiguration<E>,
                                                private val responseInterceptorFactory: (CacheToken, Boolean, Boolean, Long) -> ResponseInterceptor<E>,
                                                private val errorInterceptorFactory: (CacheToken, Long) -> ErrorInterceptor<E>,
                                                private val cacheInterceptorFactory: (CacheToken, Long) -> CacheInterceptor<E>)
    : RxCacheTransformer
        where E : Exception,
              E : NetworkErrorProvider {

    private val instructionToken = fromInstruction(
            if (configuration.isCacheEnabled) instruction else instruction.copy(operation = DoNotCache),
            (instruction.operation as? Expiring)?.compress ?: configuration.compress,
            (instruction.operation as? Expiring)?.encrypt ?: configuration.encrypt,
            url,
            uniqueParameters
    )

    override fun apply(upstream: Observable<Any>) =
            composeInternal(upstream, false, false)

    override fun apply(upstream: Single<Any>) =
            composeInternal(upstream.toObservable(), true, false)
                    .firstOrError()!!

    override fun apply(upstream: Completable) =
            composeInternal(upstream.toObservable<Any>(), false, true)
                    .ignoreElements()!!

    private fun composeInternal(upstream: Observable<Any>,
                                isSingle: Boolean,
                                isCompletable: Boolean) =
            System.currentTimeMillis().let { start ->
                upstream.compose(errorInterceptorFactory(instructionToken, start))
                        .compose(cacheInterceptorFactory(instructionToken, start))
                        .compose(responseInterceptorFactory(instructionToken, isSingle, isCompletable, start))
            }!!

    class Factory<E> internal constructor(private val errorInterceptorFactory: (CacheToken, Long) -> ErrorInterceptor<E>,
                                          private val cacheInterceptorFactory: (CacheToken, Long) -> CacheInterceptor<E>,
                                          private val responseInterceptorFactory: (CacheToken, Boolean, Boolean, Long) -> ResponseInterceptor<E>,
                                          private val configuration: CacheConfiguration<E>)
            where E : Exception,
                  E : NetworkErrorProvider {

        fun create(instruction: CacheInstruction,
                   url: String,
                   uniqueParameters: String?) =
                RxCacheInterceptor(
                        instruction,
                        url,
                        uniqueParameters,
                        configuration,
                        responseInterceptorFactory,
                        errorInterceptorFactory,
                        cacheInterceptorFactory
                ) as RxCacheTransformer
    }
}