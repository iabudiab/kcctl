/*
 *  Copyright 2021 The original authors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.kcctl.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Paths;

import io.debezium.testing.testcontainers.Connector;
import io.debezium.testing.testcontainers.DebeziumContainer;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.kcctl.command.ApplyCommand;
import org.kcctl.support.InjectConfigurationContext;
import org.kcctl.support.InjectKafkaConnectInstance;
import org.kcctl.support.WithKafkaConnect;
import org.kcctl.util.ConfigurationContext;
import picocli.CommandLine;

// @QuarkusTest is required so that CDI injection works
@QuarkusTest
@WithKafkaConnect
@DisplayNameGeneration(ReplaceUnderscores.class)
class ApplyCommandTest {

    ApplyCommand applyCommand;
    CommandLine commandLine;
    StringWriter output;

    // The configuration context has to be injected in order to be able to initialize a kcctl command instance
    @InjectConfigurationContext
    ConfigurationContext configurationContext;

    // If interaction with the running kafka connect cluster is required then this also needs to be injected
    @InjectKafkaConnectInstance
    DebeziumContainer kafkaConnect;

    @BeforeEach
    public void setup() {
        // This is boilerplate code that has to be repeated for each tested command
        applyCommand = new ApplyCommand(configurationContext);
        commandLine = new CommandLine(applyCommand);
        output = new StringWriter();
        commandLine.setOut(new PrintWriter(output));
    }

    @AfterEach
    public void cleanup() {
        // Also boilerplate
        output.flush();
        kafkaConnect.deleteAllConnectors();
    }

    @Test
    public void should_create_connector() {
        var path = Paths.get("src", "test", "resources", "local-file-source.json");
        int exitCode = commandLine.execute("-f", path.toAbsolutePath().toString());
        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
        assertThat(output.toString().trim()).isEqualTo("Created connector local-file-source");

        kafkaConnect.ensureConnectorRegistered("local-file-source");
        kafkaConnect.ensureConnectorState("local-file-source", Connector.State.RUNNING);
        kafkaConnect.ensureConnectorTaskState("local-file-source", 0, Connector.State.RUNNING);
    }

    @Test
    public void should_update_connector() {
        var path = Paths.get("src", "test", "resources", "local-file-source.json");
        int exitCode = commandLine.execute("-f", path.toAbsolutePath().toString());
        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
        assertThat(output.toString().trim()).isEqualTo("Created connector local-file-source");

        path = Paths.get("src", "test", "resources", "local-file-source-update.json");
        exitCode = commandLine.execute("-f", path.toAbsolutePath().toString());
        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
        assertThat(output.toString()).isEqualTo("""
            Created connector local-file-source
            Updated connector local-file-source
            """);

        kafkaConnect.ensureConnectorRegistered("local-file-source");
        kafkaConnect.ensureConnectorState("local-file-source", Connector.State.RUNNING);
        kafkaConnect.ensureConnectorTaskState("local-file-source", 0, Connector.State.RUNNING);
    }
}
