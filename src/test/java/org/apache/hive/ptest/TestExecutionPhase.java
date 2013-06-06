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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.hive.ptest.conf.QFileTestBatch;
import org.apache.hive.ptest.conf.TestBatch;
import org.apache.hive.ptest.conf.UnitTestBatch;
import org.apache.hive.ptest.ssh.NonZeroExitCodeException;
import org.apache.hive.ptest.ssh.SSHCommand;
import org.approvaltests.Approvals;
import org.approvaltests.reporters.JunitReporter;
import org.approvaltests.reporters.UseReporter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

@UseReporter(JunitReporter.class)
public class TestExecutionPhase extends AbstractTestPhase {
  private static final String DRIVER = "driver";
  private static final String QFILENAME = "sometest";
  private ExecutionPhase phase;
  private File baseDir;
  private File scriptDir;
  private File logDir;
  private File succeededLogDir;
  private File failedLogDir;
  private File testDir;
  private List<String> messages;
  private Set<String> failedTests;
  private List<TestBatch> testBatches;
  private TestBatch testBatch;

  
  @Before
  public void setup() throws Exception {
    super.setup();
    baseDir = createBaseDir(this.getClass().getSimpleName());
    scriptDir = Dirs.create(new File(baseDir, "scripts"));
    logDir = Dirs.create(new File(baseDir, "logs"));
    succeededLogDir = Dirs.create(new File(logDir, "succeeded"));
    failedLogDir = Dirs.create(new File(logDir, "failed"));
    scriptDir = Dirs.create(new File(baseDir, "scripts"));
    messages = Lists.newArrayList();
    failedTests = Sets.newHashSet();
  }
  private ExecutionPhase getPhase() throws IOException {
    phase = new ExecutionPhase(drones, executor, sshCommandExecutor, 
        scpCommandExecutor, rsyncCommandExecutor, localCommandFactory, templateDefaults, baseDir,
        scriptDir, succeededLogDir, failedLogDir, messages, Suppliers.ofInstance(testBatches), 
        failedTests);
    return phase;
  }
  private void setupQFile(boolean isParallel) throws Exception {
    testDir = Dirs.create( new File(baseDir, "test"));
    Assert.assertTrue(new File(testDir, QFILENAME).createNewFile());
    testBatch = new QFileTestBatch(DRIVER, "qfile", Sets.newHashSet(QFILENAME), isParallel);
    testBatches = Collections.singletonList(testBatch);
  }
  private void setupUnitTest() throws Exception {
    testBatch = new UnitTestBatch(DRIVER, false);
    testBatches = Collections.singletonList(testBatch);
  }
  @After
  public void teardown() {
    FileUtils.deleteQuietly(baseDir);
  }
  @Test
  public void testPassingQFileTest() throws Throwable {
    setupQFile(true);
    getPhase().execute();
    Approvals.verify(getExecutedCommands());
    Assert.assertEquals(Sets.newHashSet(), failedTests);
    Assert.assertEquals(1, messages.size());
  }
  @Test
  public void testFailingQFile() throws Throwable {
    setupQFile(true);
    sshCommandExecutor = new MockSSHCommandExecutor() {
      public void execute(SSHCommand command) {
        commands.add(command.getCommand());
        command.setExitCode(1);
        command.setOutput("");
      }
    };
    String junitOutput = Phase.readResource("TEST-SomeTest-failure.xml");
    File driverFailureDir = Dirs.create(new File(failedLogDir, testBatch.getName()));
    File junitOutputFile = new File(driverFailureDir, "TEST-SomeTest-failure.xml");
    Files.write(junitOutput.getBytes(Charsets.UTF_8), junitOutputFile);
    try {
      getPhase().execute();
      Assert.fail("Expected TestsFailedException");
    } catch (TestsFailedException e) {
     Assert.assertEquals("1 test batches failed", e.getMessage());
    }
    Approvals.verify(getExecutedCommands());
    Assert.assertEquals(Sets.newHashSet("SomeTest." + QFILENAME), failedTests);
    Assert.assertEquals(2, messages.size());
  }
  @Test
  public void testPassingUnitTest() throws Throwable {
    setupUnitTest();
    getPhase().execute();
    Approvals.verify(getExecutedCommands());
    Assert.assertEquals(Sets.newHashSet(), failedTests);
    Assert.assertEquals(1, messages.size());
  }
  @Test
  public void testFailingUnitTest() throws Throwable {
    setupUnitTest();
    when(localCommand.getExitCode()).thenReturn(1, 0);
    try {
      getPhase().execute();
      Assert.fail();
    } catch (TestsFailedException e) {
      Assert.assertEquals("1 test batches failed", e.getMessage());
    }
    Approvals.verify(getExecutedCommands());
    Assert.assertEquals(Sets.newHashSet(), failedTests);
    Assert.assertEquals(messages.toString(), 2, messages.size());
  }
  @Test(expected = NonZeroExitCodeException.class)
  public void testFailedCopy() throws Throwable {
    setupUnitTest();
    when(localCommand.getExitCode()).thenReturn(1);
    getPhase().execute();
  }
}