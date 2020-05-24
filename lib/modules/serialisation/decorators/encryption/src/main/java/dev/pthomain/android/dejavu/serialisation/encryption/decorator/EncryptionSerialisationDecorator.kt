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

package dev.pthomain.android.dejavu.serialisation.encryption.decorator

import dev.pthomain.android.dejavu.cache.metadata.token.instruction.RequestMetadata
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.operation.Operation.Remote.Cache
import dev.pthomain.android.dejavu.serialisation.SerialisationDecorator
import dev.pthomain.android.dejavu.serialisation.SerialisationException
import dev.pthomain.android.mumbo.base.EncryptionManager

/**
 * Optional encryption step of the serialisation process
 *
 * @param encryptionManager an instance of EncryptionManager
 * @see dev.pthomain.android.dejavu.configuration.DejaVu.Configuration.Builder.withEncryption
 */
internal class EncryptionSerialisationDecorator(
        private val requestMetadataPredicate: (RequestMetadata<*>) -> Boolean,
        private val encryptionManager: EncryptionManager
) : SerialisationDecorator {

    override val uniqueName = "ENCRYPT"

    /**
     * @return true if the decoration should apply to the given RequestMetadata
     */
    override fun appliesTo(metadata: RequestMetadata<*>) = requestMetadataPredicate(metadata)

    /**
     * Implements optional encryption during the serialisation process.
     *
     * @param responseWrapper the wrapper associated with the payload being serialised
     * @param metadata the overall metadata associated with the current serialisation
     * @param payload the payload being serialised
     * @return the encrypted payload
     * @throws SerialisationException in case this encryption step failed
     */
    @Throws(SerialisationException::class)
    override fun <R : Any> decorateSerialisation(
            responseClass: Class<R>,
            operation: Cache,
            payload: ByteArray
    ) =
            with(encryptionManager) {
                when {
                    isEncryptionAvailable -> encryptBytes(payload, DATA_TAG)
                    else -> null
                } ?: throw SerialisationException("Could not encrypt data")
            }

    /**
     * Implements optional decryption during the deserialisation process.
     *
     * @param instructionToken the request's instruction token associated with the payload being deserialised
     * @param metadata the overall metadata associated with the current serialisation
     * @param payload the payload being serialised
     * @return the decrypted payload
     * @throws SerialisationException in case this decryption step failed
     */
    @Throws(SerialisationException::class)
    override fun <R : Any> decorateDeserialisation(
            responseClass: Class<R>,
            operation: Cache,
            payload: ByteArray
    ) =
            with(encryptionManager) {
                when {
                    isEncryptionAvailable -> decryptBytes(payload, DATA_TAG)
                    else -> null
                } ?: throw SerialisationException("Could not decrypt data")
            }

    companion object {
        internal const val DATA_TAG = "DATA_TAG"
    }

}