package org.kcctl.support;

import javax.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.quarkus.test.junit.QuarkusTestExtension;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.extension.ExtendWith;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestProfile(IntegrationTestProfile.class)
@ExtendWith(KafkaConnectExtension.class)
public @interface WithKafkaConnect {

}
