/*
 *  Copyright 2017 Expedia, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.expedia.www.haystack.stitched.span.collector.writers

import com.expedia.open.tracing.stitch.StitchedSpan

/**
  * this class contains stitched span object and its serialized bytes. the consumers like
  * (see the [[com.expedia.www.haystack.stitched.span.collector.writers.StitchedSpanWriter]] class)
  * have the option to use one or both without the need to serialize/deserialize
  * @param stitchedSpan deserialized stitched span object
  * @param stitchedSpanBytes serialized stitched span bytes
  */
case class StitchedSpanDataRecord(stitchedSpan: StitchedSpan, stitchedSpanBytes: Array[Byte])
