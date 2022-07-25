/**
 * Copyright 2017 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package io.confluent.ksql.rest.server.computation;

import java.io.Closeable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the logic of reading distributed commands, including pre-existing commands that were
 * issued before being initialized, and then delegating their execution to a
 * {@link StatementExecutor}. Also responsible for taking care of any exceptions that occur in the
 * process.
 */
public class CommandRunner implements Runnable, Closeable {

  private static final Logger log = LoggerFactory.getLogger(CommandRunner.class);

  private final StatementExecutor statementExecutor;
  private final CommandStore commandStore;
  private final AtomicBoolean closed;

  public CommandRunner(
      final StatementExecutor statementExecutor,
      final CommandStore commandStore
  ) {
    this.statementExecutor = statementExecutor;
    this.commandStore = commandStore;

    closed = new AtomicBoolean(false);
  }

  /**
   * Begin a continuous poll-execute loop for the command topic, stopping only if either a
   * {@link WakeupException} is thrown or the {@link #close()} method is called.
   */
  @Override
  public void run() {
    try {
      while (!closed.get()) {
        log.debug("Polling for new writes to command topic");
        fetchAndRunCommands();
      }
    } catch (final WakeupException wue) {
      if (!closed.get()) {
        throw wue;
      }
    }
  }

  /**
   * Halt the poll-execute loop.
   */
  @Override
  public void close() {
    closed.set(true);
    commandStore.close();
  }

  void fetchAndRunCommands() {
    final ConsumerRecords<CommandId, Command> records = commandStore.getNewCommands();
    log.trace("Found {} new writes to command topic", records.count());
    for (final ConsumerRecord<CommandId, Command> record : records) {
      final CommandId commandId = record.key();
      final Command command = record.value();
      if (command != null) {
        executeStatement(command, commandId);
      }
    }
  }

  /**
   * Read and execute all commands on the command topic, starting at the earliest offset.
   * @throws Exception TODO: Refine this.
   */
  public void processPriorCommands() throws Exception {
    final RestoreCommands restoreCommands = commandStore.getRestoreCommands();
    statementExecutor.handleRestoration(restoreCommands);
  }

  private void executeStatement(final Command command, final CommandId commandId) {
    log.info("Executing statement: " + Base64.getEncoder()
        .encodeToString(commandId.toString().getBytes()));
    try {
      statementExecutor.handleStatement(command, commandId);
    } catch (final WakeupException wue) {
      throw wue;
    } catch (final Exception exception) {
      final StringWriter stringWriter = new StringWriter();
      final PrintWriter printWriter = new PrintWriter(stringWriter);
      exception.printStackTrace(printWriter);
      log.error("Exception encountered during poll-parse-execute loop: " + stringWriter.toString());
    }
  }
}
