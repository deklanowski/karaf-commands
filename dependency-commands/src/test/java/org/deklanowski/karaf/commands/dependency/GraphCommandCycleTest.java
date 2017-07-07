/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.deklanowski.karaf.commands.dependency;

import org.apache.karaf.features.Dependency;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.FeaturesService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class GraphCommandCycleTest {


    @Mock
    private FeaturesService service;


    private Feature root = new org.apache.karaf.features.internal.model.Feature("root", "1.0.0");
    private Feature a = new org.apache.karaf.features.internal.model.Feature("a", "1.0.0");
    private Feature b = new org.apache.karaf.features.internal.model.Feature("b", "1.0.0");


    private List<Dependency> rootDependencies = new ArrayList<Dependency>()
    {
        {
            add(new org.apache.karaf.features.internal.model.Dependency("a", "1.0.0"));
        }
    };


    private List<Dependency> aDependencies = new ArrayList<Dependency>()
    {
        {
            add(new org.apache.karaf.features.internal.model.Dependency("b", "1.0.0"));
        }
    };


    private List<Dependency> bDependencies = new ArrayList<Dependency>()
    {
        {
            add(new org.apache.karaf.features.internal.model.Dependency("root", "1.0.0"));
        }
    };


    @Before
    public void setUp() throws Exception {


        Field field = ReflectionUtils.findField(org.apache.karaf.features.internal.model.Feature.class,"feature");

        ReflectionUtils.makeAccessible(field);

        ReflectionUtils.setField(field,root,rootDependencies);

        ReflectionUtils.setField(field,a,aDependencies);

        ReflectionUtils.setField(field,b,bDependencies);


    }

    @Test
    public void test_graph_command() throws Exception {


        when(service.getFeatures("root", "1.0.0")).thenReturn(new Feature[]{root});

        when(service.getFeatures("a", "1.0.0")).thenReturn(new Feature[]{a});

        when(service.getFeatures("b", "1.0.0")).thenReturn(new Feature[]{b});


        graph command = new graph();
        command.setFeaturesService(service);
        command.setName(root.getName());
        command.setVersion(root.getVersion());

        command.execute();

    }
}
