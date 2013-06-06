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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import junit.framework.Assert;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public abstract class AbstractTestPhase {
  protected static final String LOCAL_DIR = "/some/local/dir";
  protected static final String WORKING_DIR = "/some/working/dir";
  protected static final String PRIVATE_KEY = "some.private.key";
  protected static final String USER = "someuser";
  protected static final String HOST = "somehost";
  protected static final int INSTANCE = 13;
  protected static final String INSTANCE_NAME = HOST + "-" + USER + "-" + INSTANCE;
  protected static final String INSTANCE_DIR =  LOCAL_DIR + "/" + INSTANCE_NAME;
  protected static final String BRANCH = "branch";
  protected static final String REPOSITORY = "repository";
  protected static final String REPOSITORY_NAME = "repositoryName";
  protected ExecutorService executor;
  protected MockLocalCommandFactory localCommandFactory;
  protected LocalCommand localCommand;
  protected MockSSHCommandExecutor sshCommandExecutor;
  protected MockSCPCommandExecutor scpCommandExecutor;
  protected MockRSyncCommandExecutor rsyncCommandExecutor;
  protected ImmutableMap<String, String> templateDefaults;
  protected List<Drone> drones;
  
  public void setup() throws Exception {
    executor = Executors.newCachedThreadPool();
    localCommandFactory = new MockLocalCommandFactory();
    localCommand = mock(LocalCommand.class);
    localCommandFactory.setInstance(localCommand);
    sshCommandExecutor = spy(new MockSSHCommandExecutor());
    scpCommandExecutor = spy(new MockSCPCommandExecutor());
    rsyncCommandExecutor = spy(new MockRSyncCommandExecutor());
    
    templateDefaults = ImmutableMap.<String, String>builder()
        .put("localDir", LOCAL_DIR)
        .put("workingDir", WORKING_DIR)
        .put("instanceName", INSTANCE_NAME)
        .put("branch", BRANCH)
        .put("repository", REPOSITORY)
        .put("repositoryName", REPOSITORY_NAME)
        .build();
    drones = Arrays.asList(new Drone[] {
        new Drone(PRIVATE_KEY, USER, HOST, INSTANCE, LOCAL_DIR),  
    });
  }
  private static boolean isOSX() {
    String osName = System.getProperty("os.name");
    return osName.contains("OS X");
  }
  protected File createBaseDir(String name) throws IOException {
    File baseDir;
    if(isOSX()) {
      // else osx gives ugly temp path which screws up approvals
      baseDir = new File("/tmp/hive-ptest-units", name);
    } else {
      baseDir = new File(new File(System.getProperty("java.io.tmpdir"), "hive-ptest-units"), name);
    }
    return Dirs.create(baseDir);    
  }
  protected String getExecutedCommands() {
    List<String> result = Lists.newArrayList();
    result.addAll(returnNotNull(sshCommandExecutor.getCommands()));
    result.addAll(returnNotNull(localCommandFactory.getCommands()));
    result.addAll(returnNotNull(scpCommandExecutor.getCommands()));
    result.addAll(returnNotNull(rsyncCommandExecutor.getCommands()));
    return Joiner.on("\n").join(result);
  }
  static <V> V returnNotNull(V value) {
    Assert.assertNotNull(value);
    return value;
  }
}
