/*
 * Copyright (c) 2014, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.dart.server.internal.local.operation;

import com.google.dart.server.AnalysisServer;
import com.google.dart.server.VersionConsumer;
import com.google.dart.server.internal.local.LocalAnalysisServerImpl;

/**
 * An operation for {@link AnalysisServer#getVersion(VersionConsumer)}.
 * 
 * @coverage dart.server.local
 */
public class GetVersionOperation implements ServerOperation {
  private final VersionConsumer consumer;

  public GetVersionOperation(VersionConsumer consumer) {
    this.consumer = consumer;
  }

  @Override
  public ServerOperationPriority getPriority() {
    return ServerOperationPriority.VERSION;
  }

  @Override
  public void performOperation(LocalAnalysisServerImpl server) throws Exception {
    server.internalGetVersion(consumer);
  }
}