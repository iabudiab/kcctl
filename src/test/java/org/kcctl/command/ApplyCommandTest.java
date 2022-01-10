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

import java.nio.file.Paths;

import io.debezium.testing.testcontainers.Connector;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.kcctl.command.ApplyCommand;
import org.kcctl.support.InjectCommandContext;
import org.kcctl.support.KcctlCommandContext;
import org.kcctl.support.WithKafkaConnect;
import picocli.CommandLine;

// @QuarkusTest is required so that CDI injection works
@QuarkusTest
// JUnit5 extension
@WithKafkaConnect
@DisplayNameGeneration(ReplaceUnderscores.class)
class ApplyCommandTest {

    // Using the extension can prevent boilerplate code
    // Everything that's required for testing the command can be encapsulated into this context
    @InjectCommandContext
    KcctlCommandContext<ApplyCommand> context;

    // This also can be refactored into the extension if need be
    @AfterEach
    public void cleanup() {
        context.output().flush();
        context.kafkaConnect().deleteAllConnectors();
    }

    @Test
    public void should_create_connector() {
        var path = Paths.get("src", "test", "resources", "local-file-source.json");
        int exitCode = context.commandLine().execute("-f", path.toAbsolutePath().toString());
        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
        assertThat(context.output().toString().trim()).isEqualTo("Created connector local-file-source");

        context.kafkaConnect().ensureConnectorRegistered("local-file-source");
        context.kafkaConnect().ensureConnectorState("local-file-source", Connector.State.RUNNING);
        context.kafkaConnect().ensureConnectorTaskState("local-file-source", 0, Connector.State.RUNNING);
    }

    @Test
    public void should_update_connector() {
        var path = Paths.get("src", "test", "resources", "local-file-source.json");
        int exitCode = context.commandLine().execute("-f", path.toAbsolutePath().toString());
        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
        assertThat(context.output().toString().trim()).isEqualTo("Created connector local-file-source");

        path = Paths.get("src", "test", "resources", "local-file-source-update.json");
        exitCode = context.commandLine().execute("-f", path.toAbsolutePath().toString());
        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
        assertThat(context.output().toString()).isEqualTo("""
            Created connector local-file-source
            Updated connector local-file-source
            """);

        context.kafkaConnect().ensureConnectorRegistered("local-file-source");
        context.kafkaConnect().ensureConnectorState("local-file-source", Connector.State.RUNNING);
        context.kafkaConnect().ensureConnectorTaskState("local-file-source", 0, Connector.State.RUNNING);
    }
}
