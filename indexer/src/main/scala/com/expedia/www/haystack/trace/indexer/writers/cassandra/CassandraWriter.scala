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

package com.expedia.www.haystack.trace.indexer.writers.cassandra

import java.nio.ByteBuffer
import java.util.Date
import java.util.concurrent.Semaphore

import com.datastax.driver.core._
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.expedia.open.tracing.buffer.SpanBuffer
import com.expedia.www.haystack.trace.indexer.config.entities.CassandraConfiguration
import com.expedia.www.haystack.trace.indexer.metrics.{AppMetricNames, MetricsSupport}
import com.expedia.www.haystack.trace.indexer.writers.TraceWriter
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor
import scala.util.Try

class CassandraWriter(config: CassandraConfiguration)(implicit val dispatcher: ExecutionContextExecutor)
  extends TraceWriter with MetricsSupport {

  private val LOGGER = LoggerFactory.getLogger(classOf[CassandraWriter])

  private val writeTimer = metricRegistry.timer(AppMetricNames.CASSANDRA_WRITE_TIME)
  private val writeFailures = metricRegistry.meter(AppMetricNames.CASSANDRA_WRITE_FAILURE)
  private val sessionFactory = new CassandraSessionFactory(config)

  // this semaphore controls the parallel writes to cassandra
  private val inflightRequestsSemaphore = new Semaphore(config.maxInFlightRequests, true)

  //insert into table(id, ts, span) values (?, ?, ?) using ttl ?
  private lazy val insertSpan: PreparedStatement = {
    import QueryBuilder.{bindMarker, ttl}
    import Schema._

    sessionFactory.session.prepare(
      QueryBuilder
        .insertInto(config.tableName)
        .value(ID_COLUMN_NAME, bindMarker(ID_COLUMN_NAME))
        .value(TIMESTAMP_COLUMN_NAME, bindMarker(TIMESTAMP_COLUMN_NAME))
        .value(SPANS_COLUMN_NAME, bindMarker(SPANS_COLUMN_NAME))
        .using(ttl(config.recordTTLInSec)))
  }

  /**
    * writes the traceId and its spans to cassandra. Use the current timestamp as the sort key for the writes to same
    * TraceId. Also if the parallel writes exceed the max inflight requests, then we block and this puts backpressure on
    * upstream
    * @param traceId: trace id
    * @param spanBuffer: list of spans belonging to this traceId - span buffer
    * @param spanBufferBytes: list of spans belonging to this traceId - serialized bytes of span buffer
    * @param isLastSpanBuffer tells if this is the last record, so the writer can flush
    * @return
    */
  override def writeAsync(traceId: String, spanBuffer: SpanBuffer, spanBufferBytes: Array[Byte], isLastSpanBuffer: Boolean): Unit = {
    var isSemaphoreAcquired = false

    try {
      inflightRequestsSemaphore.acquire()
      isSemaphoreAcquired = true

      val timer = writeTimer.time()

      // prepare the statement
      val statement = prepareStatement(traceId, spanBufferBytes)
      val asyncResult = sessionFactory.session.executeAsync(statement)
      asyncResult.addListener(new CassandraWriteResultListener(asyncResult, timer, inflightRequestsSemaphore), dispatcher)
    } catch {
      case ex: Exception =>
        LOGGER.error("Fail to write the spans to cassandra with exception", ex)
        writeFailures.mark()
        if(isSemaphoreAcquired) inflightRequestsSemaphore.release()
    }
  }

  private def prepareStatement(traceId: String, spansByte: Array[Byte]): Statement = {
    new BoundStatement(insertSpan)
      .setString(Schema.ID_COLUMN_NAME, traceId)
      .setTimestamp(Schema.TIMESTAMP_COLUMN_NAME, new Date())
      .setBytes(Schema.SPANS_COLUMN_NAME, ByteBuffer.wrap(spansByte))
      .setConsistencyLevel(config.consistencyLevel)
  }

  override def close(): Unit = {
    LOGGER.info("Closing cassandra session now..")
    Try(sessionFactory.close())
  }
}
