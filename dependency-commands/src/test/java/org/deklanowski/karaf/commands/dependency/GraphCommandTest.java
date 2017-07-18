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
public class GraphCommandTest {


    @Mock
    private FeaturesService service;


    private Feature root = new org.apache.karaf.features.internal.model.Feature("root", "1.0.0");
    private Feature a = new org.apache.karaf.features.internal.model.Feature("a", "1.0.0");
    private Feature b = new org.apache.karaf.features.internal.model.Feature("b", "1.0.0");


    private List<Dependency> rootDependencies = new ArrayList<Dependency>() {
        {
            add(new org.apache.karaf.features.internal.model.Dependency("a", "1.0.0"));
        }
    };


    private List<Dependency> aDependencies = new ArrayList<Dependency>() {
        {
            add(new org.apache.karaf.features.internal.model.Dependency("b", "1.0.0"));
        }
    };


    private List<Dependency> bDependencies = new ArrayList<Dependency>() {
        {
            add(new org.apache.karaf.features.internal.model.Dependency("root", "1.0.0"));
        }
    };


    @Before
    public void setUp() throws Exception {


        Field field = ReflectionUtils.findField(org.apache.karaf.features.internal.model.Feature.class, "feature");

        ReflectionUtils.makeAccessible(field);

        ReflectionUtils.setField(field, root, rootDependencies);

        ReflectionUtils.setField(field, a, aDependencies);


        field = ReflectionUtils.findField(org.apache.karaf.features.internal.model.Feature.class, "repositoryUrl");

        ReflectionUtils.makeAccessible(field);

        ReflectionUtils.setField(field, root, "root:url");

        ReflectionUtils.setField(field, a, "a:url");

        ReflectionUtils.setField(field, b, "b:url");

        when(service.getFeatures("root", "1.0.0")).thenReturn(new Feature[]{root});

        when(service.getFeatures("a", "1.0.0")).thenReturn(new Feature[]{a});

        when(service.getFeatures("b", "1.0.0")).thenReturn(new Feature[]{b});

        when(service.getFeature("root", "1.0.0")).thenReturn(root);

        when(service.getFeature("a", "1.0.0")).thenReturn(a);

        when(service.getFeature("b", "1.0.0")).thenReturn(b);

    }

    @Test
    public void test_graph_command() throws Exception {

        // b has no dependencies for this test
        Field field = ReflectionUtils.findField(org.apache.karaf.features.internal.model.Feature.class, "feature");
        ReflectionUtils.setField(field, b, Collections.emptyList());


        graph command = new graph();
        command.setFeaturesService(service);
        command.setName(root.getName());
        command.setVersion(root.getVersion());

        command.execute();

    }


    @Test(expected = IllegalStateException.class)
    public void test_detect_cycle_graph_command() throws Exception {

        // b contains a cycle i.e., it depends on root
        Field field = ReflectionUtils.findField(org.apache.karaf.features.internal.model.Feature.class, "feature");
        ReflectionUtils.setField(field, b, bDependencies);

        graph command = new graph();
        command.setFeaturesService(service);
        command.setName(root.getName());
        command.setVersion(root.getVersion());

        command.execute();

    }
}
