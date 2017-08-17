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

package com.expedia.www.haystack.stitch.span.collector.unit

import com.expedia.open.tracing.{Process, Span, Tag}
import com.expedia.www.haystack.stitch.span.collector.config.entities.{IndexAttribute, IndexConfiguration}
import com.expedia.www.haystack.stitch.span.collector.writers.es.SpanIndexDocumentGenerator
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.easymock.EasyMockSugar

class SpanIndexDocumentGeneratorSpec extends FunSpec with Matchers with EasyMockSugar {

  describe("Span to IndexDocument Generator") {
    it ("should extract serviceName, operationName, duration and create json document for indexing") {
      val generator = new SpanIndexDocumentGenerator(IndexConfiguration("service", "operation", "duration"))

      val span_1 = Span.newBuilder().setTraceId("traceId")
        .setProcess(Process.newBuilder().setServiceName("service1"))
        .setOperationName("op1")
        .build()
      val span_2 = Span.newBuilder().setTraceId("traceId")
        .setProcess(Process.newBuilder().setServiceName("service2"))
        .setDuration(1000L)
        .setOperationName("op2").build()

      val json = generator.create(List(span_1, span_2))
      json shouldEqual "{\"doc\":[{\"operation\":\"op1\",\"service\":\"service1\",\"duration\":0},{\"operation\":\"op2\",\"service\":\"service2\",\"duration\":1000}],\"upsert\":[{\"operation\":\"op1\",\"service\":\"service1\",\"duration\":0},{\"operation\":\"op2\",\"service\":\"service2\",\"duration\":1000}]}"
    }

    it ("should extract tags along with serviceName, operationName and duration and create json document for indexing") {
      val indexableTags = Map(
        "role" -> IndexAttribute(name = "role", `type` = "string"),
        "error" -> IndexAttribute(name = "error", `type` = "long"))
      val generator = new SpanIndexDocumentGenerator(IndexConfiguration("service", "operation", "duration", indexableTags))

      val tag_1 = Tag.newBuilder().setKey("role").setType(Tag.TagType.STRING).setVStr("haystack").build()
      val tag_2  = Tag.newBuilder().setKey("error").setType(Tag.TagType.LONG).setVLong(3).build()

      val span_1 = Span.newBuilder().setTraceId("traceId")
        .setProcess(Process.newBuilder().setServiceName("service1"))
        .setOperationName("op1")
        .addTags(tag_1)
        .build()
      val span_2 = Span.newBuilder().setTraceId("traceId")
        .setProcess(Process.newBuilder().setServiceName("service2"))
        .setDuration(1000L)
        .addTags(tag_2)
        .setOperationName("op2").build()

      val json = generator.create(List(span_1, span_2))
      json shouldEqual "{\"doc\":[{\"role\":\"haystack\",\"operation\":\"op1\",\"service\":\"service1\",\"duration\":0},{\"operation\":\"op2\",\"error\":3,\"service\":\"service2\",\"duration\":1000}],\"upsert\":[{\"role\":\"haystack\",\"operation\":\"op1\",\"service\":\"service1\",\"duration\":0},{\"operation\":\"op2\",\"error\":3,\"service\":\"service2\",\"duration\":1000}]}"
    }
  }
}