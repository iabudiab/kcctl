package org.kcctl.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import io.debezium.testing.testcontainers.DebeziumContainer;
import io.quarkus.test.common.QuarkusTestResourceConfigurableLifecycleManager;
import org.junit.Assert;
import org.kcctl.util.ConfigurationContext;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class KafkaConnectTestResource implements QuarkusTestResourceConfigurableLifecycleManager<WithKafkaConnect> {

    protected static final Network network = Network.newNetwork();

    protected static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
            .withNetwork(network);

    protected static DebeziumContainer kafkaConnect = new DebeziumContainer(DockerImageName.parse("debezium/connect:1.7.1.Final"))
            .withNetwork(network)
            .withKafka(kafka)
            .dependsOn(kafka);

    private ConfigurationContext configurationContext;

    @Override
    public void init(WithKafkaConnect annotation) {
        Arrays.asList(annotation.mountResources())
                .forEach(resource -> kafkaConnect.withCopyFileToContainer(MountableFile.forClasspathResource(resource), "/tmp/" + resource));
    }

    @Override
    public void inject(TestInjector testInjector) {
        // This is the only injection point when using QuarkusTestResource
        testInjector.injectIntoFields(configurationContext, new TestInjector.AnnotatedAndMatchesType(InjectConfigurationContext.class, ConfigurationContext.class));
        testInjector.injectIntoFields(kafkaConnect, new TestInjector.AnnotatedAndMatchesType(InjectKafkaConnectInstance.class, DebeziumContainer.class));
    }

    @Override
    public Map<String, String> start() {
        Startables.deepStart(kafka, kafkaConnect).join();

        initializeConfigurationContext();

        return Map.of(
                "kafka.connect.cluster", kafkaConnect.getTarget(),
                "kafka.connect.username", "testuser",
                "kafka.connect.password", "testpassword"
        );
    }

    private void initializeConfigurationContext() {
        try {
            Path tempDirectory = Files.createTempDirectory(Paths.get("target"), "integration-test");
            var configFile = tempDirectory.resolve(".kcctl");

            Files.writeString(configFile, String.format("""
                    {
                        "currentContext": "local",
                        "local": {
                            "cluster": "%s",
                            "username": "testuser",
                            "password": "testpassword"
                        }
                    }
                    """, kafkaConnect.getTarget()));

            this.configurationContext = new ConfigurationContext(tempDirectory.toFile());
        }
        catch (IOException e) {
            Assert.fail("Couldn't initialize configuration context: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        kafkaConnect.stop();
        kafka.stop();
    }
}
