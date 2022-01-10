package org.kcctl.support;

import java.io.StringWriter;

import io.debezium.testing.testcontainers.DebeziumContainer;
import picocli.CommandLine;

public record KcctlCommandContext<T>(DebeziumContainer kafkaConnect, T command, CommandLine commandLine, StringWriter output) {

}
