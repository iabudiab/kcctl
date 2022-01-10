package org.kcctl.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.TestProfile;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestProfile(IntegrationTestProfile.class)
@QuarkusTestResource(value = KafkaConnectTestResource.class, restrictToAnnotatedClass = true)
public @interface WithKafkaConnect {

    String[] mountResources() default "";
}
