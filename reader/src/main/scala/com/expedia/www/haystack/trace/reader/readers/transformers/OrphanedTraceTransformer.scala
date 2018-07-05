/*
 *  Copyright 2017 Expedia, Inc.
 *
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 */

package com.expedia.www.haystack.trace.reader.readers.transformers

import com.expedia.open.tracing.Span
import com.expedia.www.haystack.trace.reader.readers.utils.SpanUtils

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


/**
  * If the root span is missing within a trace, create a pseudo root span to wrap all the spans.
  *
  * ** [[com.expedia.www.haystack.trace.reader.readers.validators.RootValidator]] and [[com.expedia.www.haystack.trace.reader.readers.validators.ParentIdValidator]] must be turned off for this to take into effect. **
  * ** Should place first in the POST transformers sequence of the configuration, as the other transformers may depend on or use the generated root during their transformation. **
  */
object OrphanedTraceTransformerConstants {
  val AUTO_GEN_REASON = "Missing root span"
}

class OrphanedTraceTransformer extends TraceTransformer {

  override def transform(spans: Seq[Span]): Seq[Span] = {
    val orphanedParents = findOrphanedParentsGroupedByParentId(spans)
    if (orphanedParents.isEmpty) {
      spans
    } else if (multipleOrphans(orphanedParents)) {
      Seq.empty
    } else {
      val rootSpan = generateRootSpan(orphanedParents)
      spans :+ rootSpan
    }
  }

  def findOrphanedParentsGroupedByParentId(spans: Seq[Span]): mutable.HashMap[String, ArrayBuffer[Span]] = {
    val keyMap = new mutable.HashMap[String, ArrayBuffer[Span]]
    var keySet = new mutable.HashSet[String]
    for (span <- spans) {
      keySet += span.getSpanId
      if (!span.getParentSpanId.isEmpty) {
        keyMap.getOrElseUpdate(span.getParentSpanId, ArrayBuffer(span)) += span
      }
    }
    keySet.foreach(key => {
      keyMap.remove(key)
    })
    keyMap
  }

  def multipleOrphans(orphanedParents: mutable.HashMap[String, ArrayBuffer[Span]]): Boolean = {
    orphanedParents.size != 1 || orphanedParents.head._2(0).getParentSpanId != orphanedParents.head._2(0).getTraceId
  }

  def generateRootSpan(orphanedParents: mutable.HashMap[String, ArrayBuffer[Span]]): Span = {
    val span = orphanedParents.head._2(0)
    SpanUtils.createAutoGeneratedRootSpan(orphanedParents.head._2, OrphanedTraceTransformerConstants.AUTO_GEN_REASON, span.getTraceId).build()
  }
}