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

package dev.pthomain.android.dejavu.cache.metadata.response

import dev.pthomain.android.dejavu.cache.metadata.token.ResponseToken
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.*

/**
 * Default implementation of CacheMetadata.Holder. Have the response extend this class
 * if you want to inherit from the default metadata holding and error handling mechanisms.
 * Alternatively, if your response class cannot extend this class, have it implement the
 * CacheMetadata.Holder interface in a similar fashion as this class' implementation.
 * To provide your own error handling via an error factory, see GlitchFactory.
 *
 * @see dev.pthomain.android.dejavu.interceptors.error.glitch.GlitchFactory
 */
abstract class Cacheable<R : Any> : HasMetadata<R, Remote, ResponseToken<Remote, R>> {

    @Transient
    override lateinit var callDuration: CallDuration

    @Transient
    override lateinit var cacheToken: ResponseToken<Remote, R>
}