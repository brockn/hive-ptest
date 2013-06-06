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
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.hive.ptest.LocalCommand.CollectLogPolicy;
import org.apache.hive.ptest.ssh.NonZeroExitCodeException;
import org.apache.hive.ptest.ssh.RSyncCommand;
import org.apache.hive.ptest.ssh.RSyncCommandExecutor;
import org.apache.hive.ptest.ssh.RSyncResult;
import org.apache.hive.ptest.ssh.SCPCommand;
import org.apache.hive.ptest.ssh.SCPCommandExecutor;
import org.apache.hive.ptest.ssh.SCPResult;
import org.apache.hive.ptest.ssh.SSHCommand;
import org.apache.hive.ptest.ssh.SSHCommandExecutor;
import org.apache.hive.ptest.ssh.SSHExecutionException;
import org.apache.hive.ptest.ssh.SSHResult;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;

public abstract class Phase {
  
  private final List<Drone> dronesList;
  private final ExecutorService executor;
  private final SSHCommandExecutor sshCommandExecutor;
  private final SCPCommandExecutor scpCommandExecutor;
  private final RSyncCommandExecutor rsyncCommandExecutor;
  private final LocalCommandFactory localCommandFactory;
  private final ImmutableMap<String, String> templateDefaults;
  
  public Phase(List<Drone> drones, ExecutorService executor,
      SSHCommandExecutor sshCommandExecutor,
      SCPCommandExecutor scpCommandExecutor,
      RSyncCommandExecutor rsyncCommandExecutor,
      LocalCommandFactory localCommandFactory,
      ImmutableMap<String, String> templateDefaults) {
    super();
    this.dronesList = drones;
    this.executor = executor;
    this.sshCommandExecutor = sshCommandExecutor;
    this.scpCommandExecutor = scpCommandExecutor;
    this.rsyncCommandExecutor = rsyncCommandExecutor;
    this.localCommandFactory = localCommandFactory;
    this.templateDefaults = templateDefaults;
  }

  public abstract void execute() throws Throwable;
  
  protected void execLocally(String command) 
      throws IOException, InterruptedException, NonZeroExitCodeException {
    CollectLogPolicy localCollector = new CollectLogPolicy();
    command = getTemplateResult(command, templateDefaults);
    LocalCommand localCmd = localCommandFactory.create(localCollector, command);
    if(localCmd.getExitCode() != 0) {
      throw new NonZeroExitCodeException(String.format(
          "Command '%s' failed with exit status %d and output '%s'", 
          command, localCmd.getExitCode(), localCollector.getOutput()));
    }
  }
  
  protected List<RSyncResult> rsyncFromLocalToRemoteInstances(String localFile, String remoteFile)
      throws InterruptedException, IOException {
    List<RSyncCommand> cmds = Lists.newArrayList();
    for(Drone drone : dronesList ) {
      Map<String, String> templateVariables = Maps.newHashMap(templateDefaults);
      templateVariables.put("instanceName", drone.getInstanceName());
      templateVariables.put("localDir", drone.getLocalDirectory());
      cmds.add(new RSyncCommand(rsyncCommandExecutor, drone.getPrivateKey(), drone.getUser(),
          drone.getHost(), drone.getInstance(), 
          getTemplateResult(localFile, templateVariables), 
          getTemplateResult(remoteFile, templateVariables), 
          RSyncCommand.Type.FROM_LOCAL));
    }
    List<RSyncResult> results = Lists.newArrayList();
    for(Future<RSyncResult> future : executor.invokeAll(cmds)) {
      try {
        RSyncResult result = future.get();
        if(result.getException() != null || result.getExitCode() != 0) {
          throw new SSHExecutionException(result);
        }
        results.add(result);
      } catch (InterruptedException e) {
        throw e;
      } catch (Exception e) {
        throw Throwables.propagate(e);
      }
    }
    return results;
  }
  protected SCPResult copyFromDroneToLocal(Drone drone, String localFile, String remoteFile)
      throws SSHExecutionException, IOException {
    Map<String, String> templateVariables = Maps.newHashMap(templateDefaults);
    templateVariables.put("instanceName", drone.getInstanceName());
    templateVariables.put("localDir", drone.getLocalDirectory());
    SCPResult result = new SCPCommand(scpCommandExecutor, drone.getPrivateKey(), drone.getUser(),
        drone.getHost(), drone.getInstance(), 
        getTemplateResult(localFile, templateVariables), 
        getTemplateResult(remoteFile, templateVariables), 
        SCPCommand.Type.TO_LOCAL).call();
    if(result.getException() != null || result.getExitCode() != 0) {
      throw new SSHExecutionException(result);
    }
    return result;
  }
  protected SCPResult copyToDroneFromLocal(Drone drone, String localFile, String remoteFile)
      throws SSHExecutionException, IOException {
    Map<String, String> templateVariables = Maps.newHashMap(templateDefaults);
    templateVariables.put("instanceName", drone.getInstanceName());
    templateVariables.put("localDir", drone.getLocalDirectory());
    SCPResult result = new SCPCommand(scpCommandExecutor, drone.getPrivateKey(), drone.getUser(),
        drone.getHost(), drone.getInstance(), 
        getTemplateResult(localFile, templateVariables), 
        getTemplateResult(remoteFile, templateVariables), 
        SCPCommand.Type.FROM_LOCAL).call();
    if(result.getException() != null || result.getExitCode() != 0) {
      throw new SSHExecutionException(result);
    }
    return result;
  }
  protected List<SSHResult> execHosts(String command) 
      throws SSHExecutionException, InterruptedException, IOException {
    List<SSHCommand> cmds = Lists.newArrayList();
    Set<String> hosts = Sets.newHashSet();
    for(Drone drone : dronesList) {
      Map<String, String> templateVariables = Maps.newHashMap(templateDefaults);
      templateVariables.put("instanceName", drone.getInstanceName());
      templateVariables.put("localDir", drone.getLocalDirectory());
      command = getTemplateResult(command, templateVariables);
      if(hosts.add(drone.getHost())) {
        cmds.add(new SSHCommand(sshCommandExecutor, drone.getPrivateKey(), drone.getUser(),
            drone.getHost(), drone.getInstance(), command));
      }
    }
    List<SSHResult> results = Lists.newArrayList();
    for(Future<SSHResult> future : executor.invokeAll(cmds)) {
      try {
        SSHResult result = future.get();
        if(result.getException() != null) {
          if(result.getException() instanceof InterruptedException) {
            throw (InterruptedException)result.getException();
          }
          throw new SSHExecutionException(result);
        }
        results.add(result);
      } catch (InterruptedException e) {
        throw e;
      } catch (ExecutionException e) {
        if(e.getCause() != null) {
          Throwables.propagateIfPossible(e.getCause(), IOException.class);
          throw Throwables.propagate(e.getCause());
        }
        throw Throwables.propagate(e);
      }
    }
    return results;
  }
  protected List<SSHResult> execInstances(String command) 
      throws SSHExecutionException, InterruptedException, IOException {
    List<SSHCommand> cmds = Lists.newArrayList();
    for(Drone drone : dronesList ) {
      Map<String, String> templateVariables = Maps.newHashMap(templateDefaults);
      templateVariables.put("instanceName", drone.getInstanceName());
      templateVariables.put("localDir", drone.getLocalDirectory());
      cmds.add(new SSHCommand(sshCommandExecutor, drone.getPrivateKey(), drone.getUser(),
          drone.getHost(), drone.getInstance(), getTemplateResult(command, templateVariables)));
    }
    List<SSHResult> results = Lists.newArrayList();
    for(Future<SSHResult> future : executor.invokeAll(cmds)) {
      try {
        SSHResult result = future.get();
        if(result.getException() != null || result.getExitCode() != 0) {
          throw new SSHExecutionException(result);
        }
        results.add(result);
      } catch (InterruptedException e) {
        throw e;
      } catch (ExecutionException e) {
        if(e.getCause() != null) {
          throw Throwables.propagate(e.getCause());          
        }
        throw Throwables.propagate(e);
      }
    }
    return results;
  }
  
  protected static void writeTemplateResult(String resource, File script, 
      Map<String, String> keyValues) throws IOException {
    String template = readResource(resource);
    PrintWriter writer = new PrintWriter(script);
    try {
      writer.write(getTemplateResult(template, keyValues));
      if(writer.checkError()) {
        throw new IOException("Error writing to " + script);
      }
    } finally {
      writer.close();      
    }
  }
  protected static String readResource(String resource) throws IOException {
    return Resources.toString(Resources.getResource(resource), Charsets.UTF_8);
  }
  protected static String getTemplateResult(String command, Map<String, String> keyValues)
      throws IOException {
    VelocityContext context = new VelocityContext();
    for(String key : keyValues.keySet()) {
      context.put(key, keyValues.get(key));
    }
    StringWriter writer = new StringWriter();
    if(!Velocity.evaluate(context, writer, command, command)) {
      throw new IOException("Unable to render " + command + " with " + keyValues);
    }
    writer.close();
    return writer.toString();
  }
  protected ExecutorService getExecutor() {
    return executor;
  }
  protected SSHCommandExecutor getSshCommandExecutor() {
    return sshCommandExecutor;
  }
  protected LocalCommandFactory getLocalCommandFactory() {
    return localCommandFactory;
  }
  protected ImmutableMap<String, String> getTemplateDefaults() {
    return templateDefaults;
  }
}
