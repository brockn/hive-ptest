/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.hive.ptest;

import junit.framework.Assert;

import org.junit.Test;

import com.google.common.base.Strings;

public class TestLocalCommand {

  @Test
  public void testSuccess() throws Exception {
    LocalCommand.CollectPolicy output = new LocalCommand.CollectPolicy();
    LocalCommand command = (new LocalCommandFactory()).create(output, "echo 123");
    Thread.sleep(500L);
    Assert.assertEquals(0, command.getExitCode());
    Assert.assertEquals(0, command.getExitCode());
    Assert.assertEquals("123", Strings.nullToEmpty(output.getOutput()).trim());
  }
  @Test
  public void testFailure() throws Exception {
    LocalCommand.CollectPolicy output = new LocalCommand.CollectPolicy();
    LocalCommand command = (new LocalCommandFactory()).create(output, "exit 1");
    Assert.assertEquals(1, command.getExitCode());
    Assert.assertEquals(1, command.getExitCode());
    Assert.assertEquals("", output.getOutput());
  }
}
