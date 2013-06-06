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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;

import com.google.common.collect.Sets;


public class SurefireReportParser {

  private final Set<String> failedTests;
  
  public SurefireReportParser(List<File> directories) throws Exception {
    org.apache.maven.plugins.surefire.report.SurefireReportParser parser = 
        new org.apache.maven.plugins.surefire.report.SurefireReportParser(directories, Locale.US);
    failedTests = Sets.newHashSet();
    for(ReportTestSuite suite : parser.parseXMLReportFiles()) {
      if(suite.getNumberOfFailures() > 0 || suite.getNumberOfErrors() > 0) {
        for(ReportTestCase testCase : suite.getTestCases()) {
          Map<String, Object> failure = testCase.getFailure();
          if(failure != null && failure.size() > 0) {
            failedTests.add(testCase.getFullName());
          }
        }        
      }
    }
  }
  public Set<String> getFailedTests() {
    return failedTests;
  }
}
