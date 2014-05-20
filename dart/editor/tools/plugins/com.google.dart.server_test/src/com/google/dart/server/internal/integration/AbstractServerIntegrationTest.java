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
package com.google.dart.server.internal.integration;

import com.google.dart.server.AnalysisServer;
import com.google.dart.server.VersionConsumer;

import junit.framework.TestCase;

public abstract class AbstractServerIntegrationTest extends TestCase {

  protected AnalysisServer server;

  public void test_getVersion() throws Exception {
    final String[] versionPtr = {null};
    server.getVersion(new VersionConsumer() {
      @Override
      public void computedVersion(String version) {
        versionPtr[0] = version;
      }
    });
    waitForAllServerResponses();
    assertEquals("0.0.1", versionPtr[0]);
  }

  protected abstract void initServer();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initServer();
  }

  @Override
  protected void tearDown() throws Exception {
    server.shutdown();
    server = null;
    super.tearDown();
  }

  protected abstract void waitForAllServerResponses() throws Exception;
}
