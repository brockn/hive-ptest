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

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.hive.ptest.ssh.RSyncCommandExecutor;
import org.apache.hive.ptest.ssh.SCPCommandExecutor;
import org.apache.hive.ptest.ssh.SSHCommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

public class PrepPhase extends Phase {
  private static final Logger LOG = LoggerFactory
      .getLogger(PrepPhase.class);
  private final File scratchDir;
  private final File patchFile;
  private final ExecutorService rsyncExecutor;
  
  public PrepPhase(List<Drone> drones, ExecutorService executor,
      SSHCommandExecutor sshCommandExecutor,
      SCPCommandExecutor scpCommandExecutor,
      RSyncCommandExecutor rsyncCommandExecutor,
      LocalCommandFactory localCommandFactory,
      ImmutableMap<String, String> templateDefaults,
      File scratchDir, File patchFile, ExecutorService rsyncExecutor) {
    super(drones, executor, sshCommandExecutor, scpCommandExecutor,
        rsyncCommandExecutor, localCommandFactory, templateDefaults);
    this.scratchDir = scratchDir;
    this.patchFile = patchFile;
    this.rsyncExecutor = rsyncExecutor;
  }
  public void execute() throws Exception {
    try {
      long prepStart = System.currentTimeMillis();
      execLocally("mkdir -p $workingDir/scratch $workingDir/isolated/logs $workingDir/isolated/maven");
      execLocally("mkdir -p $workingDir/isolated/scratch $workingDir/isolated/ivy $workingDir/isolated/${repositoryName}-source");
      execInstances("mkdir -p $localDir/$instanceName/logs $localDir/$instanceName/maven $localDir/$instanceName/scratch");
      execInstances("mkdir -p $localDir/$instanceName/ivy $localDir/$instanceName/${repositoryName}-source");
      if(patchFile != null) {
        execLocally("cp -f " + patchFile.getPath() + " $workingDir/scratch/build.patch");
      }
      {
        long start = System.currentTimeMillis();
        File sourcePrepScript = new File(scratchDir, "source-prep.sh");
        writeTemplateResult("source-prep.vm", sourcePrepScript, getTemplateDefaults());
        execLocally("bash " + sourcePrepScript.getPath());
        LOG.debug("Deleting " + sourcePrepScript + ": " + sourcePrepScript.delete());
        long elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
            TimeUnit.MILLISECONDS);
        LOG.info("PERF: source prep took " + elapsedTime + " minutes");
      }
      Future<Void> rysncSource = rsyncExecutor.submit(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          long start = System.currentTimeMillis();
          rsyncFromLocalToRemoteInstances("$workingDir/${repositoryName}-source", "$localDir/$instanceName/");
          long elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
              TimeUnit.MILLISECONDS);
          LOG.info("PERF: rsync source took " + elapsedTime + " minutes");
          return null;
        }        
      });
      Future<Void> rysncMaven = rsyncExecutor.submit(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          long start = System.currentTimeMillis();
          rsyncFromLocalToRemoteInstances("$workingDir/maven", "$localDir/$instanceName/");
          long elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
              TimeUnit.MILLISECONDS);
          LOG.info("PERF: rsync maven took " + elapsedTime + " minutes");
          return null;
        }
      });
      Future<Void> rysncIvy = rsyncExecutor.submit(new Callable<Void>() {
        @Override
        public Void call() throws Exception {
          long start = System.currentTimeMillis();
          rsyncFromLocalToRemoteInstances("$workingDir/ivy", "$localDir/$instanceName/");
          long elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
              TimeUnit.MILLISECONDS);
          LOG.info("PERF: rsync ivy took " + elapsedTime + " minutes");
          return null;
        }
      });
      execLocally("rsync -qavP --delete --delete-during --force $workingDir/${repositoryName}-source $workingDir/isolated/");
      execLocally("rsync -qavP --delete --delete-during --force $workingDir/ivy $workingDir/isolated/");
      execLocally("rsync -qavP --delete --delete-during --force $workingDir/maven $workingDir/isolated/");
      rysncSource.get();
      rysncMaven.get();
      rysncIvy.get();
      long elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - prepStart),
          TimeUnit.MILLISECONDS);
      LOG.info("PERF: prep phase took " + elapsedTime + " minutes");
    } finally {
      rsyncExecutor.shutdownNow();
    }
  }
}