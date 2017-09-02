/**
 * Copyright 2016-2017 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.dependencies.elasticsearch;

import java.util.LinkedList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.spark.api.java.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Serializable;
import scala.Tuple2;
import zipkin.DependencyLink;
import zipkin.dependencies.elasticsearch.ElasticsearchDependenciesJob.SpanAcceptor;
import zipkin.internal.DependencyLinker;
import zipkin.internal.v2.Span;

final class TraceIdAndJsonToDependencyLinks implements Serializable,
    Function<Iterable<Tuple2<String, String>>, Iterable<DependencyLink>> {
  private static final long serialVersionUID = 0L;
  private static final Logger log = LoggerFactory.getLogger(TraceIdAndJsonToDependencyLinks.class);

  @Nullable final Runnable logInitializer;
  final SpanAcceptor decoder;

  TraceIdAndJsonToDependencyLinks(Runnable logInitializer, SpanAcceptor decoder) {
    this.logInitializer = logInitializer;
    this.decoder = decoder;
  }

  @Override public Iterable<DependencyLink> call(Iterable<Tuple2<String, String>> traceIdJson) {
    if (logInitializer != null) logInitializer.run();
    List<Span> sameTraceId = new LinkedList<>();
    for (Tuple2<String, String> row : traceIdJson) {
      try {
        decoder.decodeInto(row._2, sameTraceId);
      } catch (Exception e) {
        log.warn("Unable to decode span from traces where trace_id=" + row._1, e);
      }
    }
    DependencyLinker linker = new DependencyLinker();
    linker.putTrace(sameTraceId.iterator());
    return linker.link();
  }
}
