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

package dev.pthomain.android.dejavu.retrofit

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import dev.pthomain.android.dejavu.interceptors.cache.metadata.CacheMetadata
import dev.pthomain.android.dejavu.interceptors.cache.metadata.token.CacheToken
import dev.pthomain.android.dejavu.interceptors.error.ErrorInterceptor
import dev.pthomain.android.dejavu.interceptors.error.ResponseWrapper
import dev.pthomain.android.dejavu.interceptors.error.glitch.Glitch
import dev.pthomain.android.dejavu.interceptors.response.ResponseInterceptor
import dev.pthomain.android.dejavu.retrofit.annotations.AnnotationProcessor.RxType
import dev.pthomain.android.dejavu.retrofit.annotations.AnnotationProcessor.RxType.*
import dev.pthomain.android.dejavu.retrofit.annotations.CacheException
import dev.pthomain.android.dejavu.test.assertEqualsWithContext
import dev.pthomain.android.dejavu.test.network.model.TestResponse
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import retrofit2.Call
import retrofit2.CallAdapter
import java.util.*

@Suppress("UNCHECKED_CAST")
class ProcessingErrorAdapterUnitTest {

    private lateinit var mockDefaultAdapter: CallAdapter<Any, Any>
    private lateinit var mockErrorInterceptorFactory: (CacheToken) -> ErrorInterceptor<Glitch>
    private lateinit var mockResponseInterceptorFactory: (CacheToken, RxType, Long) -> ResponseInterceptor<Glitch>
    private lateinit var mockCacheToken: CacheToken
    private lateinit var mockException: CacheException
    private lateinit var mockErrorInterceptor: ErrorInterceptor<Glitch>
    private lateinit var mockResponseInterceptor: ResponseInterceptor<Glitch>
    private lateinit var mockCall: Call<Any>
    private lateinit var mockMetadata: CacheMetadata<Glitch>
    private lateinit var upstreamCaptor: ArgumentCaptor<Observable<Any>>
    private lateinit var metadataCaptor: ArgumentCaptor<CacheMetadata<Glitch>>
    private lateinit var mockResponseWrapper: ResponseWrapper<Glitch>

    private val mockStart = 1234L

    private lateinit var targetFactory: ProcessingErrorAdapter.Factory<Glitch>

    @Before
    fun setUp() {
        mockDefaultAdapter = mock()
        mockErrorInterceptorFactory = mock()
        mockResponseInterceptorFactory = mock()
        mockCacheToken = mock()
        mockException = mock()
        mockErrorInterceptor = mock()
        mockResponseInterceptor = mock()
        mockCall = mock()

        mockMetadata = CacheMetadata(
                mock(),
                mock(),
                CacheMetadata.Duration(0, 0, 0)
        )

        mockResponseWrapper = ResponseWrapper(
                TestResponse::class.java,
                null,
                mockMetadata
        )

        upstreamCaptor = ArgumentCaptor.forClass(Observable::class.java) as ArgumentCaptor<Observable<Any>>
        metadataCaptor = ArgumentCaptor.forClass(CacheMetadata::class.java) as ArgumentCaptor<CacheMetadata<Glitch>>

        targetFactory = ProcessingErrorAdapter.Factory(
                mockErrorInterceptorFactory,
                mockResponseInterceptorFactory,
                { Date(4321L) }
        )
    }

    private fun createTarget(mockRxType: RxType): CallAdapter<Any, Any> {
        whenever(mockErrorInterceptorFactory.invoke(
                eq(mockCacheToken)
        )).thenReturn(mockErrorInterceptor)

        whenever(mockResponseInterceptorFactory.invoke(
                eq(mockCacheToken),
                eq(mockRxType),
                eq(mockStart)
        )).thenReturn(mockResponseInterceptor)

        return targetFactory.create(
                mockDefaultAdapter,
                mockCacheToken,
                1234L,
                mockRxType,
                mockException
        ) as CallAdapter<Any, Any>
    }

    @Test
    fun testAdaptObservable() {
        testAdapt(OBSERVABLE)
    }

    @Test
    fun testAdaptSingle() {
        testAdapt(SINGLE)
    }

    private fun testAdapt(rxType: RxType) {
        whenever(mockErrorInterceptor.apply(any())).thenReturn(Observable.just(mockResponseWrapper))
        whenever(mockResponseInterceptor.apply(any())).thenReturn(Observable.just(mockResponseWrapper))

        val adapted = createTarget(rxType).adapt(mockCall)

        val wrapper = when (rxType) {
            OBSERVABLE -> (adapted as Observable<Any>).blockingFirst()
            SINGLE -> (adapted as Single<Any>).blockingGet()
            COMPLETABLE -> (adapted as Completable).blockingAwait()
        }

        assertEqualsWithContext(
                mockResponseWrapper,
                wrapper,
                "The adapted call return the wrong response wrapper"
        )
    }

}