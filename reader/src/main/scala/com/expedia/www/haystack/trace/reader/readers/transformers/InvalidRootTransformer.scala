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

import java.util.UUID

import com.expedia.open.tracing.{Span, Tag}
import com.expedia.www.haystack.trace.reader.readers.utils.SpanUtils

/**
  *
  * If there are multiple roots in the given trace, use the first root based on startTime to be root
  * mark other roots as children of the selected root
  * If there is no root, assume loopback span or first span in time order to be root
  *
  * **Apply this transformer only if you are not confident about clients sending in roots properly**
  */
class InvalidRootTransformer extends TraceTransformer {
  private val AUTOGEN_REASON =
    """
      |This span is autogenerated by haystack and only a UI sugar to show multiple root spans together in one view".
      |This is a symptom that few spans have empty parent id, but only one such root span should exist.
    """.stripMargin

  override def transform(spans: Seq[Span]): Seq[Span] = {
    val roots = spans.filter(span => span.getParentSpanId.isEmpty)

    roots.size match {
      case 0 => toTraceWithAssumedRoot(spans)
      case 1 => spans
      case _ => toTraceWithSingleRoot(spans, roots.size)
    }
  }

  private def toTraceWithAssumedRoot(spans: Seq[Span]) = {
    val possibleRoots = spans.filter(span => span.getParentSpanId == span.getSpanId)

    if(possibleRoots.size <= 1) {
      val earliestRoot = spans.minBy(_.getStartTime)
      // convert the possible root span into the actual root.
      val assumedRoot = possibleRoots.headOption.getOrElse(earliestRoot)
      spans.map(span => if (span == assumedRoot) Span.newBuilder(span).setParentSpanId("").build() else span)
    } else {
      // create an autogenerated span and make it as a root
      val autoGeneratedSpan = createAutoGeneratedRootSpan(spans)
        .addTags(Tag.newBuilder().setKey("X-HAYSTACK-SPAN-ROOT-COUNT").setVLong(0).setType(Tag.TagType.LONG))
        .build

      spans.+:(SpanUtils.addClientLogTag(autoGeneratedSpan))
    }
  }

  private def createAutoGeneratedRootSpan(spans: Seq[Span]): Span.Builder = {
    val earliestRoot = spans.minBy(_.getStartTime)
    val longestDurationSpan = spans.maxBy(span => span.getStartTime + span.getDuration)

    val startTime = earliestRoot.getStartTime
    val duration = (longestDurationSpan.getStartTime + longestDurationSpan.getDuration) - startTime
    val autoGeneratedSpanID = UUID.randomUUID().toString

    Span.newBuilder()
      .setServiceName(earliestRoot.getServiceName)
      .setOperationName("auto-generated")
      .setTraceId(spans.head.getTraceId)
      .setSpanId(autoGeneratedSpanID)
      .setParentSpanId("")
      .setStartTime(startTime)
      .setDuration(duration)
      .addTags(Tag.newBuilder().setKey("X-HAYSTACK-AUTOGEN-REASON").setVStr(AUTOGEN_REASON).setType(Tag.TagType.STRING))
      .addTags(Tag.newBuilder().setKey("X-HAYSTACK-AUTOGEN-SPAN-ID").setVStr(autoGeneratedSpanID).setType(Tag.TagType.STRING))
      .addTags(Tag.newBuilder().setKey("X-HAYSTACK-AUTOGEN").setVBool(true).setType(Tag.TagType.BOOL))
  }

  private def toTraceWithSingleRoot(spans: Seq[Span], rootSpanCount: Int): Seq[Span] = {
    val rootSpan = createAutoGeneratedRootSpan(spans)
      .addTags(Tag.newBuilder().setKey("X-HAYSTACK-SPAN-ROOT-COUNT").setVLong(rootSpanCount).setType(Tag.TagType.LONG))
      .build()

    spans
      .map(span => if (span.getParentSpanId.isEmpty) span.toBuilder.setParentSpanId(rootSpan.getSpanId).build() else span)
      .+:(SpanUtils.addClientLogTag(rootSpan))
  }
}
