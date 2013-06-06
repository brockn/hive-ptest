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
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.hive.ptest.conf.Configuration;
import org.apache.hive.ptest.conf.Host;
import org.apache.hive.ptest.conf.TestParser;
import org.apache.hive.ptest.ssh.RSyncCommandExecutor;
import org.apache.hive.ptest.ssh.SCPCommandExecutor;
import org.apache.hive.ptest.ssh.SSHCommandExecutor;
import org.apache.velocity.app.Velocity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.Resources;

public class RunTests {

  static {
    Velocity.init();
  }
  private static final Logger LOG = LoggerFactory
      .getLogger(RunTests.class);


  private final Configuration configuration;
  private final ExecutorService executor;
  private final List<String> messages;
  private final Set<String> failedTests;
  private final List<Phase> phases;
  
  public RunTests(final Configuration configuration, LocalCommandFactory localCommandFactory,
      SSHCommandExecutor sshCommandExecutor, SCPCommandExecutor scpCommandExecutor,
      RSyncCommandExecutor rsyncCommandExecutor) 
    throws Exception {
    this.configuration = configuration;
    messages = Lists.newArrayList();
    failedTests = Sets.newTreeSet();
    List<Drone> drones = Lists.newArrayList();
    for(Host host : configuration.getHosts()) {
      String[] localDirs = host.getLocalDirectories();
      for (int index = 0; index < host.getThreads(); index++) {
        drones.add(new Drone(configuration.getPrivateKey(), host.getUser(), host.getName(),
            index, localDirs[index % localDirs.length]));     
      }
    }
    executor = Executors.newFixedThreadPool(drones.size() * 3);
    String buildTag = System.getenv("BUILD_TAG") == null ? 
        "undefined-" + System.currentTimeMillis() : System.getenv("BUILD_TAG");
    File logDir = Dirs.create(new File(configuration.getWorkingDirectory(),
        String.format("logs/%s", buildTag)));
    File failedLogDir = Dirs.create(new File(logDir, "failed"));
    File succeededLogDir = Dirs.create(new File(logDir, "succeeded"));
    File scratchDir = Dirs.createEmpty(new File(configuration.getWorkingDirectory(), "scratch"));
    File patchDir = Dirs.createEmpty(new File(logDir, "patches"));
    File patchFile = null;
    if(configuration.getPatch() != null) {
      patchFile = new File(patchDir, buildTag + ".patch");
      Files.write(Resources.toByteArray(new URL(configuration.getPatch())), patchFile);
    }
    ImmutableMap.Builder<String, String> templateDefaultsBuilder = ImmutableMap.builder();
    
    templateDefaultsBuilder.
        put("repository", configuration.getRepository()).
        put("repositoryName", configuration.getRepositoryName()).
        put("branch", configuration.getBranch()).
        put("workingDir", configuration.getWorkingDirectory()).
        put("antArgs", configuration.getAntArgs()).
        put("buildTag", buildTag).
        put("logDir", logDir.getAbsolutePath()).
        put("javaHome", configuration.getJavaHome()).
        put("antEnvOpts", configuration.getAntEnvOpts());
    if(!configuration.getJavaHome().isEmpty()) {
      templateDefaultsBuilder.put("javaHome", configuration.getJavaHome());
    }
    ImmutableMap<String, String> templateDefaults = templateDefaultsBuilder.build();
    
    TestParser testParser = new TestParser(configuration.getContext(),
        new File(configuration.getWorkingDirectory(), configuration.getRepositoryName() + "-source"));
    phases = Lists.newArrayList();
    phases.add(new CleanupPhase(drones, executor, sshCommandExecutor, scpCommandExecutor, 
        rsyncCommandExecutor, localCommandFactory, templateDefaults));
    phases.add(new PrepPhase(drones, executor, sshCommandExecutor, scpCommandExecutor, 
        rsyncCommandExecutor, localCommandFactory, templateDefaults, scratchDir, patchFile, Executors.newFixedThreadPool(3)));
    phases.add(new ExecutionPhase(drones, executor, sshCommandExecutor, scpCommandExecutor,
        rsyncCommandExecutor, localCommandFactory, templateDefaults, new File(configuration.getWorkingDirectory()),
        scratchDir, succeededLogDir, failedLogDir, messages, testParser.parse(),
        failedTests));
   
  }
  public void run() {
    boolean error = false;
    Map<String, Long> elapsedTimes = Maps.newTreeMap();
    try {
      LOG.info("Running tests with " + configuration);
      for(Phase phase : phases) {
        LOG.info("Executing " + phase.getClass().getName());
        long start = System.currentTimeMillis();
        try {
          phase.execute();          
        } finally {
          long elapsedTime = TimeUnit.MINUTES.convert((System.currentTimeMillis() - start),
              TimeUnit.MILLISECONDS);
          elapsedTimes.put(phase.getClass().getSimpleName(), elapsedTime);
        }
      }
    } catch(Throwable throwable) {
      LOG.error("Test run exited with an unexpected error", throwable);
      error = true;
    } finally {
      executor.shutdownNow();
      for(String message : messages) {
        System.out.println(message);
      }
      System.out.println(String.format("%d failed tests", failedTests.size()));
      for(String failingTestName : failedTests) {
        System.out.println(failingTestName);
      }
      for(Map.Entry<String, Long> entry : elapsedTimes.entrySet()) {
        System.out.println(String.format("PERF: Phase %s took %d minutes", entry.getKey(), entry.getValue()));
      }
      if(error || !failedTests.isEmpty()) {
        System.exit(1);
      }
    }
  }

  private static final String PROPERTIES = "properties";
  private static final String REPOSITORY = "repository";
  private static final String REPOSITORY_NAME = "repositoryName";
  private static final String BRANCH = "branch";
  private static final String PATCH = "patch";
  private static final String JAVA_HOME = "javaHome";
  private static final String ANT_ENV_OPTS = "antEnvOpts";
  /**
   * All args override properties file settings except
   * for this one which is additive.
   */
  private static final String ANT_ARG = "D";
  
  public static void main(String[] args) throws Exception {
    LOG.info("Args " + Arrays.toString(args));
    CommandLineParser parser = new GnuParser();
    Options options = new Options();
    options.addOption(null, PROPERTIES, true, "properties file");
    options.addOption(null, REPOSITORY, true, "Overrides git repository in properties file");
    options.addOption(null, REPOSITORY_NAME, true, "Overrides git repository *name* in properties file");
    options.addOption(null, BRANCH, true, "Overrides git branch in properties file");
    options.addOption(null, PATCH, true, "URI to patch, either file:/// or http(s)://");
    options.addOption(ANT_ARG, null, true, "Supplemntal ant arguments");
    options.addOption(null, JAVA_HOME, true, "Java Home for compiling and running tests");
    options.addOption(null, ANT_ENV_OPTS, true, "ANT_OPTS environemnt variable setting");
    CommandLine commandLine = parser.parse(options, args);
    if(!commandLine.hasOption(PROPERTIES)) {
      throw new IllegalArgumentException(Joiner.on(" ").
          join(RunTests.class.getName(), "--" + PROPERTIES,"config.properties"));
    }
    Configuration conf = Configuration.fromFile(commandLine.getOptionValue(PROPERTIES));
    String repository = Strings.nullToEmpty(commandLine.getOptionValue(REPOSITORY)).trim();
    if(!repository.isEmpty()) {
      conf.setRepository(repository);
    }
    String repositoryName = Strings.nullToEmpty(commandLine.getOptionValue(REPOSITORY_NAME)).trim();
    if(!repositoryName.isEmpty()) {
      conf.setRepositoryName(repositoryName);
    }
    String branch = Strings.nullToEmpty(commandLine.getOptionValue(BRANCH)).trim();
    if(!branch.isEmpty()) {
      conf.setBranch(branch);
    }
    String patch = Strings.nullToEmpty(commandLine.getOptionValue(PATCH)).trim();
    if(!patch.isEmpty()) {
      conf.setPatch(patch);
    }
    String javaHome = Strings.nullToEmpty(commandLine.getOptionValue(JAVA_HOME)).trim();
    if(!javaHome.isEmpty()) {
      conf.setJavaHome(javaHome);
    }
    String antEnvOpts = Strings.nullToEmpty(commandLine.getOptionValue(ANT_ENV_OPTS)).trim();
    if(!antEnvOpts.isEmpty()) {
      conf.setAntEnvOpts(antEnvOpts);
    }        
    String[] supplementalAntArgs = commandLine.getOptionValues(ANT_ARG);
    if(supplementalAntArgs != null && supplementalAntArgs.length > 0) {
      String antArgs = Strings.nullToEmpty(conf.getAntArgs());
      if(!(antArgs.isEmpty() || antArgs.endsWith(" "))) {
        antArgs += " ";
      }
      antArgs += "-" + ANT_ARG + Joiner.on(" -" + ANT_ARG).join(supplementalAntArgs);      
      conf.setAntArgs(antArgs);
    }
    RunTests ptest = new RunTests(conf, new LocalCommandFactory(), 
        new SSHCommandExecutor(), new SCPCommandExecutor(), new RSyncCommandExecutor());
    ptest.run();
  }    
}