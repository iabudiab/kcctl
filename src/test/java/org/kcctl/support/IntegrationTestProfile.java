package org.kcctl.support;

import java.util.Set;

import io.quarkus.test.junit.QuarkusTestProfile;

public class IntegrationTestProfile implements QuarkusTestProfile {

    @Override
    public Set<String> tags() {
        return Set.of("integration-test");
    }
}
