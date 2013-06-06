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

import static org.mockito.Mockito.when;

import java.util.List;

import junit.framework.Assert;

import org.apache.hive.ptest.ssh.NonZeroExitCodeException;
import org.junit.Before;
import org.junit.Test;


public class TestPhase extends AbstractTestPhase {

  private Phase phase;
  @Before
  public void setup() throws Exception {
    super.setup();
  } 
  
  @Test(expected = NonZeroExitCodeException.class)
  public void testExecLocallyFails() throws Throwable {
    phase = new Phase(drones, executor, sshCommandExecutor, 
        scpCommandExecutor, rsyncCommandExecutor, localCommandFactory,
        templateDefaults) {
          @Override
          public void execute() throws Exception {
            execLocally("local");
          }
    };
    when(localCommand.getExitCode()).thenReturn(1);
    phase.execute();
  }
  @Test
  public void testExecLocallySucceeds() throws Throwable {
    phase = new Phase(drones, executor, sshCommandExecutor, 
        scpCommandExecutor, rsyncCommandExecutor, localCommandFactory,
        templateDefaults) {
          @Override
          public void execute() throws Exception {
            execLocally("local");
          }
    };
    phase.execute();
    List<String> commands = localCommandFactory.getCommands();
    Assert.assertEquals(1, commands.size());
    Assert.assertEquals("local", commands.get(0));
  }
}
