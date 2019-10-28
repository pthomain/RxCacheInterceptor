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

package dev.pthomain.android.dejavu.interceptors.error

import dev.pthomain.android.dejavu.configuration.error.NetworkErrorPredicate
import dev.pthomain.android.dejavu.interceptors.cache.metadata.CacheMetadata

/**
 * Wraps the call and associated metadata for internal use.
 *
 * @param responseClass the target response class
 * @param response the call's response if available
 * @param metadata the call's metadata
 */
//TODO add type T to this to handle CacheOperation<T>
data class ResponseWrapper<E>(val responseClass: Class<*>,
                              val response: Any?,
                              @Transient override var metadata: CacheMetadata<E>)
    : CacheMetadata.Holder<E>
        where E : Exception,
              E : NetworkErrorPredicate
