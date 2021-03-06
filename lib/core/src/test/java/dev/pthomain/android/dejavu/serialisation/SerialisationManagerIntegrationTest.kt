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

package dev.pthomain.android.dejavu.serialisation

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import dev.pthomain.android.boilerplate.core.utils.lambda.Action
import dev.pthomain.android.dejavu.configuration.error.glitch.Glitch
import dev.pthomain.android.dejavu.serialisation.SerialisationManager.Factory.Type.FILE
import dev.pthomain.android.dejavu.serialisation.decoration.SerialisationDecorationMetadata
import dev.pthomain.android.dejavu.shared.metadata.token.InstructionToken
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Remote.Cache
import dev.pthomain.android.dejavu.test.BaseIntegrationTest
import dev.pthomain.android.dejavu.test.assertResponseWrapperWithContext
import dev.pthomain.android.glitchy.core.interceptor.error.glitch.Glitch
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class SerialisationManagerIntegrationTest
    : BaseIntegrationTest<dev.pthomain.android.dejavu.serialisation.SerialisationManager<Glitch>>({
    it.serialisationManagerFactory().create(FILE) //TODO test the factory
}) {

    private lateinit var wrapper: ResponseWrapper<*, *, Glitch>
    private lateinit var instructionToken: InstructionToken
    private lateinit var mockErrorCallback: Action

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        instructionToken = instructionToken(Cache())
        mockErrorCallback = mock()

        wrapper = getStubbedTestResponse(instructionToken)
    }

    //FIXME use cases

    @Test
    @Throws(Exception::class)
    fun testCompress() {
        val compressed = target.serialise(
                wrapper,
                SerialisationDecorationMetadata(true, false)
        )

        assertEquals(
                "Wrong compressed size",
                2566,
                compressed.size
        )
    }

    @Test
    @Throws(Exception::class)
    fun testUncompressSuccess() {
        val compressed = target.serialise(
                wrapper,
                SerialisationDecorationMetadata(true, false)
        )

        val uncompressed = target.deserialise(
                instructionToken,
                compressed,
                SerialisationDecorationMetadata(true, false)
        )

        assertResponseWrapperWithContext(
                wrapper,
                uncompressed,
                "Response wrapper didn't match"
        )

        verify(mockErrorCallback, never()).invoke()
    }

    @Test
    @Throws(Exception::class)
    fun testUncompressFailure() {
        val compressed = target.serialise(
                wrapper,
                SerialisationDecorationMetadata(true, false)
        )

        for (i in 0..49) {
            compressed[i] = 0
        }

        target.deserialise(
                instructionToken,
                compressed,
                SerialisationDecorationMetadata(true, false)
        )

        verify(mockErrorCallback).invoke()
    }

    //TODO test encryption
}