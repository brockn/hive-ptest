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

import java.io.IOException;

import org.apache.hive.ptest.LocalCommand;
import org.apache.hive.ptest.LocalCommand.CollectPolicy;


public class RSyncCommandExecutor {

  public void execute(RSyncCommand command) {
    CollectPolicy collector = new CollectPolicy();
    try {
      LocalCommand cmd;
      if(command.getType() == RSyncCommand.Type.TO_LOCAL) {
        throw new UnsupportedOperationException(String.valueOf(command.getType()));
      } else if(command.getType() == RSyncCommand.Type.FROM_LOCAL) {
        cmd = new LocalCommand(collector,
            String.format("rsync -qavPe \"ssh -i %s\" --delete --delete-during --force %s %s@%s:%s", 
                command.getPrivateKey(), command.getLocalFile(), command.getUser(), command.getHost(),
                command.getRemoteFile()));
      } else {
        throw new UnsupportedOperationException(String.valueOf(command.getType()));
      }
      command.setExitCode(cmd.getExitCode());
    } catch (IOException e) {
      command.setException(e);
    } catch (InterruptedException e) {
      command.setException(e);
    } finally {
      command.setOutput(collector.getOutput());
    }
  }
}
