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
package org.apache.hive.ptest.ssh;

import org.apache.hive.ptest.LocalCommand;
import org.apache.hive.ptest.LocalCommand.CollectPolicy;

public class SSHCommandExecutor {

  public void execute(SSHCommand command) {
    CollectPolicy collector = new CollectPolicy();
    try {
      LocalCommand cmd = new LocalCommand(collector, 
          String.format("ssh -i %s -l %s %s '%s'", command.getPrivateKey(), 
              command.getUser(), command.getHost(), command.getCommand()));
      command.setExitCode(cmd.getExitCode());
    } catch (Exception e) {
      command.setException(e);
    } finally {
      command.setOutput(collector.getOutput());
    }
  }
}
