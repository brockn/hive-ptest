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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.hive.ptest.ssh.RSyncCommandExecutor;
import org.apache.hive.ptest.ssh.SCPCommandExecutor;
import org.apache.hive.ptest.ssh.SSHCommandExecutor;

import com.google.common.collect.ImmutableMap;

public class CleanupPhase extends Phase {

  public CleanupPhase(List<Drone> drones, ExecutorService executor,
      SSHCommandExecutor sshCommandExecutor,
      SCPCommandExecutor scpCommandExecutor,
      RSyncCommandExecutor rsyncCommandExecutor,
      LocalCommandFactory localCommandFactory,
      ImmutableMap<String, String> templateDefaults) {
    super(drones, executor, sshCommandExecutor, scpCommandExecutor,
        rsyncCommandExecutor, localCommandFactory, templateDefaults);
  }
  public void execute() throws Exception {
    execHosts("killall -q -9 -f java || true");
    execHosts("killall -q -9 -u hiveptest || true");
    TimeUnit.SECONDS.sleep(1);
    execLocally("rm -rf $workingDir/scratch $workingDir/isolated/scratch $workingDir/isolated/logs");
    execInstances("rm -rf $localDir/$instanceName/scratch $localDir/$instanceName/logs");
  }
}