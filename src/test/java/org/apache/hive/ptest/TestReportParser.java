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

import junit.framework.Assert;

import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class TestReportParser {

  @Test
  public void test() throws Exception {
    File reportDir = new File("src/test/resources/test-outputs");
    SurefireReportParser parser = new SurefireReportParser(Lists.newArrayList(reportDir));
    Assert.assertEquals(3, parser.getFailedTests().size());
    Assert.assertEquals(Sets.
        newHashSet("org.apache.hadoop.hive.cli.TestCliDriver.testCliDriver_skewjoin_union_remove_1",
        "org.apache.hadoop.hive.cli.TestCliDriver.testCliDriver_union_remove_9",
        "org.apache.hadoop.hive.cli.TestCliDriver.testCliDriver_skewjoin"), 
        parser.getFailedTests());
  }
}
