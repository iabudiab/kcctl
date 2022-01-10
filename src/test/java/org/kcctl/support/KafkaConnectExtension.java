package org.kcctl.support;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.function.Predicate;

import io.debezium.testing.testcontainers.DebeziumContainer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.util.AnnotationUtils;
import org.junit.platform.commons.util.ReflectionUtils;
import org.kcctl.util.ConfigurationContext;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import picocli.CommandLine;

public class KafkaConnectExtension implements BeforeAllCallback, BeforeEachCallback {

    protected static final Network network = Network.newNetwork();

    protected static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
            .withNetwork(network);

    protected static DebeziumContainer kafkaConnect = new DebeziumContainer(DockerImageName.parse("debezium/connect:1.7.1.Final"))
            .withNetwork(network)
            .withKafka(kafka)
            .dependsOn(kafka);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Startables.deepStart(kafka, kafkaConnect).join();
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        // This doesn't work!
        // The test instance here IS NOT the same as the actual instance that is initialized by the QuarkusTestExtension because quarkus uses a separate class loader
        context.getRequiredTestInstances()
                .getAllInstances()
                .forEach(instance -> this.injectFields(context, instance, instance.getClass(), ReflectionUtils::isNotStatic));
    }

    private void injectFields(ExtensionContext context, Object testInstance, Class<?> testClass, Predicate<Field> predicate) {
        AnnotationUtils.findAnnotatedFields(testClass, InjectCommandContext.class, predicate).forEach((field) -> {
            this.assertSupportedType(field.getType());

            try {
                KcctlCommandContext<?> commandContext = prepareContext(field);
                ReflectionUtils.makeAccessible(field);
                field.set(testInstance, commandContext);
            }
            catch (IllegalAccessException e) {
                throw new ExtensionConfigurationException("Couldn't inject KcctlCommandContext", e);
            }
        });
    }

    private void assertSupportedType(Class<?> type) {
        if (type != KcctlCommandContext.class) {
            throw new ExtensionConfigurationException(String.format("Can only resolve fields of type KcctlCommandContext but was: %s", type.getName()));
        }
    }

    private KcctlCommandContext<?> prepareContext(Field field) {
        Type type = field.getGenericType();
        Type genericType = ((ParameterizedType) type).getActualTypeArguments()[0];
        Class<?> targetCommand = (Class<?>) genericType;

        ensureCaseyCommand(targetCommand);
        ConfigurationContext configurationContext = initializeConfigurationContext();

        Object command = instantiateCommand(targetCommand, configurationContext);
        var commandLine = new CommandLine(command);
        var output = new StringWriter();
        commandLine.setOut(new PrintWriter(output));

        return new KcctlCommandContext<>(kafkaConnect, command, commandLine, output);
    }

    private Object instantiateCommand(Class<?> targetCommand, ConfigurationContext configurationContext) {
        try {
            Constructor<?> constructor = targetCommand.getDeclaredConstructor(ConfigurationContext.class);
            return constructor.newInstance(configurationContext);
        }
        catch (NoSuchMethodException e) {
            throw new ExtensionConfigurationException("Unsupported @CommandLine.Command type. Required a single argument constructor accepting a ConfigurationContext");
        }
        catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new ExtensionConfigurationException("Couldn't instantiate command of type " + targetCommand, e);
        }
    }

    private void ensureCaseyCommand(Class<?> targetCommand) {
        if (!targetCommand.isAnnotationPresent(CommandLine.Command.class)) {
            throw new ParameterResolutionException("KcctlCommandContext should target a type annotated with @CommandLine.Command");
        }
    }

    private ConfigurationContext initializeConfigurationContext() {
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

            return new ConfigurationContext(tempDirectory.toFile());
        }
        catch (IOException e) {
            throw new ExtensionConfigurationException("Couldn't initialize configuration context", e);
        }
    }
}
