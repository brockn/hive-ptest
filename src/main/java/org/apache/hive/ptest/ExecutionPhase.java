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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.hive.ptest.conf.TestBatch;
import org.apache.hive.ptest.ssh.AbstractSSHResult;
import org.apache.hive.ptest.ssh.NonZeroExitCodeException;
import org.apache.hive.ptest.ssh.RSyncCommandExecutor;
import org.apache.hive.ptest.ssh.SCPCommandExecutor;
import org.apache.hive.ptest.ssh.SSHCommand;
import org.apache.hive.ptest.ssh.SSHCommandExecutor;
import org.apache.hive.ptest.ssh.SSHExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ExecutionPhase extends Phase {
  private static final Logger LOG = LoggerFactory
      .getLogger(ExecutionPhase.class);
  private static final String ISOLATED_INSTANCE_NAME = "isolated";
  private final List<Drone> dronesList;
  private final List<Drone> dronePerHostList;
  private final File workingDir;
  private final File scratchDir;
  private final File succeededLogDir;
  private final File failedLogDir;
  private final File isolatedDir;
  private final CompletionService<Void> completionService;
  private final List<String> messages;
  private final BlockingQueue<TestBatch> parallelWorkQueue;
  private final BlockingQueue<TestBatch> isolatedWorkQueue;
  private final Set<String> failedTests;
  private final Supplier<List<TestBatch>> testBatchSupplier;
  
  private final List<TestBatch> failedTestResults = Collections.
      synchronizedList(new ArrayList<TestBatch>());

  
  public ExecutionPhase(List<Drone> drones, ExecutorService executor,
      SSHCommandExecutor sshCommandExecutor,
      SCPCommandExecutor scpCommandExecutor,
      RSyncCommandExecutor rsyncCommandExecutor,
      LocalCommandFactory localCommandFactory,
      ImmutableMap<String, String> templateDefaults, File workingDir, File scratchDir,
      File succeededLogDir, File failedLogDir, List<String> messages,
      Supplier<List<TestBatch>> testBatchSupplier,
      Set<String> failedTests) throws IOException {
    super(drones, executor, sshCommandExecutor, scpCommandExecutor,
        rsyncCommandExecutor, localCommandFactory, templateDefaults);
    this.dronesList = drones;
    this.dronePerHostList = Lists.newArrayList();
    this.workingDir = workingDir;
    this.scratchDir = scratchDir;
    this.succeededLogDir = succeededLogDir;
    this.failedLogDir = failedLogDir;
    this.messages = messages;
    this.testBatchSupplier = testBatchSupplier;
    this.failedTests = failedTests;
    this.isolatedDir = Dirs.create(new File(workingDir, ISOLATED_INSTANCE_NAME));
    this.completionService = new ExecutorCompletionService<Void>(getExecutor());
    this.parallelWorkQueue = new LinkedBlockingQueue<TestBatch>();
    this.isolatedWorkQueue = new LinkedBlockingQueue<TestBatch>();
    Set<String> hosts = Sets.newHashSet();
    for(Drone drone : drones) {
      if(hosts.add(drone.getHost())) {
        dronePerHostList.add(drone);
      }
    }
  }
  private void runParallelTests(List<Drone> drones, final BlockingQueue<TestBatch> workQueue)
      throws Throwable {
    long start = System.currentTimeMillis();
    try {
      for(final Drone drone : drones) {
        completionService.submit(new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            while(!workQueue.isEmpty()) {
              TestBatch batch = workQueue.poll();
              if(batch != null) {
                LOG.info("Pending results: " + workQueue.size());
                executeParallel(drone, batch);              
              }
            }
            return null;
          }
        });
      }
      int numResults = drones.size();
      for (int resultsIndex = 0; resultsIndex < numResults; resultsIndex++) {
        LOG.info("Expecting " + (numResults - resultsIndex) + " results");
        try {
          completionService.take().get();
        } catch (ExecutionException e) {
          if(e.getCause() != null) {
            throw e.getCause();
          }
          throw e; // should never occur
        }
      }
    } finally {
      long elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
          TimeUnit.MILLISECONDS);
      LOG.info("PERF: parallel tests took " + elapsedTime + " minutes");
    }
  }
  public void execute() throws Throwable {
    long start = System.currentTimeMillis();
    for(TestBatch batch : testBatchSupplier.get()) {
      if(batch.isParallel()) {
        parallelWorkQueue.add(batch);
      } else {
        isolatedWorkQueue.add(batch);
      }
    }
    /*
     * Isolated tests will be executed one of two ways:
     * 1) On the localhost while the parallel tests are executing
     * 2) After the parallel tests are complete, the isolated tests
     * will be executed in isolation on the slave hosts
     */
    // execute tests locally
    ExecutorService isolatedTestsExecutor = Executors.newSingleThreadExecutor();
    Future<Void> isolatedTestsResult = isolatedTestsExecutor.submit(new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        long start = System.currentTimeMillis();
        try {
          while(!isolatedWorkQueue.isEmpty()) {
            TestBatch batch = isolatedWorkQueue.poll();
            if(batch != null) {
              executeIsolated(batch);
            }
          }
          return null;          
        } finally {
          long elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
              TimeUnit.MILLISECONDS);
          LOG.info("PERF: isolated tests took " + elapsedTime + " minutes");
        }
      }
    });      
    try {
      // execute the parallel tests
      runParallelTests(dronesList, parallelWorkQueue);
      LOG.info("Stealing work from isolated tests. Queue Depth: " + isolatedWorkQueue.size());
      // steal isolated tests from the local executor
      runParallelTests(dronePerHostList, isolatedWorkQueue);
      try {
        isolatedTestsResult.get();
      } catch (ExecutionException e) {
        if(e.getCause() != null) {
          throw e.getCause();
        }
        throw e; // should never occur
      }
      if(!failedTestResults.isEmpty()) {
        for(TestBatch failure : failedTestResults) {
          messages.add(String.format("Test batch %s has one or more failing tests", 
              failure.getName()));
          File batchLogDir = new File(failedLogDir, failure.getName());
          SurefireReportParser parser = new SurefireReportParser(Lists.newArrayList(batchLogDir));
          for(String failedTest : parser.getFailedTests()) {
            failedTests.add(failedTest);
          }
        }
        throw new TestsFailedException(failedTestResults.size() + " test batches failed");
      }
    } finally {
      isolatedTestsExecutor.shutdown();
      long elapsed = System.currentTimeMillis() - start;
      messages.add("PERF: exec phase " + 
          TimeUnit.MINUTES.convert(elapsed, TimeUnit.MILLISECONDS) + " minutes");
    }
  }
  
  private void executeParallel(Drone drone, TestBatch batch) 
      throws IOException, SSHExecutionException {
    String scriptName = "hiveptest-" + batch.getName() + ".sh";
    File script = new File(scratchDir, scriptName);
    Map<String, String> templateVariables = Maps.newHashMap(getTemplateDefaults());
    templateVariables.put("instanceName", drone.getInstanceName());
    templateVariables.put("batchName",batch.getName());
    templateVariables.put("testArguments", batch.getTestArguments());
    templateVariables.put("localDir", drone.getLocalDirectory());
    templateVariables.put("logDir", drone.getLocalLogDirectory());
    String command = getTemplateResult("bash $localDir/$instanceName/scratch/" + script.getName(), 
        templateVariables);
    writeTemplateResult("batch-exec.vm", script, templateVariables);
    copyToDroneFromLocal(drone, script.getAbsolutePath(), "$localDir/$instanceName/scratch/" + scriptName);
    script.delete();
    LOG.info(drone + " executing " + batch + " with " + command);
    AbstractSSHResult sshResult = new SSHCommand(getSshCommandExecutor(), drone.getPrivateKey(), drone.getUser(),
        drone.getHost(), drone.getInstance(), command).
    call();
    File batchLogDir = null;
    if(sshResult.getExitCode() != 0 || sshResult.getException() != null) {
      failedTestResults.add(batch);
      batchLogDir = Dirs.create(new File(failedLogDir, batch.getName()));
    } else {
      batchLogDir = Dirs.create(new File(succeededLogDir, batch.getName()));
    }
    copyFromDroneToLocal(drone, batchLogDir.getAbsolutePath(), 
        drone.getLocalLogDirectory() + "/*");
    File logFile = new File(batchLogDir, String.format("%s.txt", batch.getName()));
    PrintWriter writer = new PrintWriter(logFile);
    writer.write(String.format("result = '%s'\n", sshResult.toString()));
    writer.write(String.format("output = '%s'\n", sshResult.getOutput()));
    if(sshResult.getException() != null) {
      sshResult.getException().printStackTrace(writer);
    }
    writer.close();
  }
  private void executeIsolated(final TestBatch batch) 
      throws IOException, InterruptedException, NonZeroExitCodeException {
    String scriptName = "hiveptest-" + batch.getName() + ".sh";
    File script = new File(isolatedDir, scriptName);
    String logDir = (new File(isolatedDir, "logs")).getAbsolutePath();
    final String command = getTemplateResult("bash " + script.getAbsolutePath(), 
        getTemplateDefaults());
    Map<String, String> templateVariables = Maps.newHashMap(getTemplateDefaults());
    templateVariables.put("instanceName", ISOLATED_INSTANCE_NAME);
    templateVariables.put("batchName", batch.getName());
    templateVariables.put("testArguments", batch.getTestArguments());
    templateVariables.put("logDir", logDir);
    templateVariables.put("localDir", workingDir.getAbsolutePath());
    writeTemplateResult("batch-exec.vm", script, templateVariables);
    LOG.info("Executing " + batch + " with " + command);
    LocalCommand.CollectPolicy logs = new LocalCommand.CollectPolicy();
    LocalCommand localCommand = getLocalCommandFactory().create(logs, command);
    File batchLogDir = null;
    if(localCommand.getExitCode() != 0) {
      failedTestResults.add(batch);
      batchLogDir = Dirs.create(new File(failedLogDir, batch.getName()));
    } else {
      batchLogDir = Dirs.create(new File(succeededLogDir, batch.getName()));
    }
    execLocally("cp -R " + logDir + "/* " + batchLogDir.getAbsolutePath());
    File logFile = new File(batchLogDir, String.format("%s.txt", batch.getName()));
    PrintWriter writer = new PrintWriter(logFile);
    writer.write(String.format("exitCode = '%d'\n", localCommand.getExitCode()));
    writer.write(String.format("output = '%s'\n", logs.getOutput()));
    writer.close();
  }
}
