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

package dev.pthomain.android.dejavu.serialisation.compression

import dev.pthomain.android.boilerplate.core.utils.log.Logger
import dev.pthomain.android.dejavu.cache.metadata.token.instruction.RequestMetadata
import dev.pthomain.android.dejavu.serialisation.SerialisationDecorator
import dev.pthomain.android.dejavu.serialisation.compression.decorator.CompressionSerialisationDecorator
import dev.pthomain.android.dejavu.utils.SilentLogger
import org.iq80.snappy.Snappy

class Compression(
        logger: Logger = SilentLogger,
        requestMetadataPredicate: (RequestMetadata<*>) -> Boolean = { true }
) : SerialisationDecorator.Provider {

    override val serialisationDecorator: SerialisationDecorator =
            CompressionSerialisationDecorator(
                    requestMetadataPredicate,
                    logger,
                    Snappy::compress,
                    Snappy::uncompress
            )
}
