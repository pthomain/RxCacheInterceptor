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

package uk.co.glass_software.android.cache_interceptor.injection

import android.content.ContentValues
import com.nhaarman.mockitokotlin2.mock
import dagger.Module
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.requery.android.database.sqlite.SQLiteDatabase
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import uk.co.glass_software.android.cache_interceptor.configuration.CacheConfiguration
import uk.co.glass_software.android.cache_interceptor.interceptors.RxCacheInterceptor
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.CacheInterceptor
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.CacheManager
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.database.DatabaseManager
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.database.SqlOpenHelper
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.serialisation.GsonSerialiser
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.serialisation.SerialisationManager
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.cache.token.CacheToken
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.error.ApiError
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.error.ErrorInterceptor
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.response.EmptyResponseFactory
import uk.co.glass_software.android.cache_interceptor.interceptors.internal.response.ResponseInterceptor
import uk.co.glass_software.android.cache_interceptor.response.CacheMetadata
import uk.co.glass_software.android.cache_interceptor.retrofit.ProcessingErrorAdapter
import uk.co.glass_software.android.cache_interceptor.retrofit.RetrofitCacheAdapterFactory
import uk.co.glass_software.android.cache_interceptor.retrofit.annotations.AnnotationProcessor
import uk.co.glass_software.android.shared_preferences.StoreEntryFactory
import uk.co.glass_software.android.shared_preferences.encryption.manager.EncryptionManager
import java.util.*

@Module
internal class UnitTestConfigurationModule
    : ConfigurationModule<ApiError> {

    override val dateFactory: (Long?) -> Date = mock()

    override fun provideConfiguration(): CacheConfiguration<ApiError> = mock()

    override fun provideGsonSerialiser(): GsonSerialiser = mock()

    override fun provideStoreEntryFactory(gsonSerialiser: GsonSerialiser): StoreEntryFactory = mock()

    override fun provideEncryptionManager(storeEntryFactory: StoreEntryFactory): EncryptionManager? = mock()

    override fun provideSerialisationManager(encryptionManager: EncryptionManager?): SerialisationManager<ApiError> = mock()

    override fun provideSqlOpenHelper(): SqlOpenHelper = mock()

    override fun provideDatabase(sqlOpenHelper: SqlOpenHelper): SQLiteDatabase = mock()

    override fun provideDatabaseManager(database: SQLiteDatabase, serialisationManager: SerialisationManager<ApiError>): DatabaseManager<ApiError> = mock()

    override fun mapToContentValues(map: Map<String, *>): ContentValues = mock()

    override fun provideCacheManager(databaseManager: DatabaseManager<ApiError>, emptyResponseFactory: EmptyResponseFactory<ApiError>): CacheManager<ApiError> = mock()

    override fun provideErrorInterceptorFactory(): ConfigurationModule.Function3<CacheToken, Long, AnnotationProcessor.RxType, ErrorInterceptor<ApiError>> = mock()

    override fun provideCacheInterceptorFactory(cacheManager: CacheManager<ApiError>): ConfigurationModule.Function2<CacheToken, Long, CacheInterceptor<ApiError>> = mock()

    override fun provideResponseInterceptor(metadataSubject: PublishSubject<CacheMetadata<ApiError>>, emptyResponseFactory: EmptyResponseFactory<ApiError>): ConfigurationModule.Function4<CacheToken, Boolean, Boolean, Long, ResponseInterceptor<ApiError>> = mock()

    override fun provideRxCacheInterceptorFactory(errorInterceptorFactory: ConfigurationModule.Function3<CacheToken, Long, AnnotationProcessor.RxType, ErrorInterceptor<ApiError>>, cacheInterceptorFactory: ConfigurationModule.Function2<CacheToken, Long, CacheInterceptor<ApiError>>, responseInterceptor: ConfigurationModule.Function4<CacheToken, Boolean, Boolean, Long, ResponseInterceptor<ApiError>>): RxCacheInterceptor.Factory<ApiError> = mock()

    override fun provideDefaultAdapterFactory(): RxJava2CallAdapterFactory = mock()

    override fun provideRetrofitCacheAdapterFactory(defaultAdapterFactory: RxJava2CallAdapterFactory, rxCacheInterceptorFactory: RxCacheInterceptor.Factory<ApiError>, processingErrorAdapterFactory: ProcessingErrorAdapter.Factory<ApiError>, annotationProcessor: AnnotationProcessor<ApiError>): RetrofitCacheAdapterFactory<ApiError> = mock()

    override fun provideProcessingErrorAdapterFactory(errorInterceptorFactory: ConfigurationModule.Function3<CacheToken, Long, AnnotationProcessor.RxType, ErrorInterceptor<ApiError>>, responseInterceptorFactory: ConfigurationModule.Function4<CacheToken, Boolean, Boolean, Long, ResponseInterceptor<ApiError>>): ProcessingErrorAdapter.Factory<ApiError> = mock()

    override fun provideCacheMetadataSubject(): PublishSubject<CacheMetadata<ApiError>> = mock()

    override fun provideCacheMetadataObservable(subject: PublishSubject<CacheMetadata<ApiError>>): Observable<CacheMetadata<ApiError>> = mock()

    override fun provideAnnotationProcessor(): AnnotationProcessor<ApiError> = mock()

    override fun provideEmptyResponseFactory(): EmptyResponseFactory<ApiError> = mock()


}
