/*
 * Copyright 2006-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.admin.service.command;

import com.consol.citrus.admin.process.listener.ProcessListener;
import org.apache.commons.lang.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for creating {@link ProcessBuilder}s. To be used for executing a shell command on unix or windows.
 *
 * @author Martin.Maher@consol.de
 */
public abstract class AbstractTerminalCommand implements TerminalCommand {

    /** Logger */
    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private static final String BASH = "bash";
    private static final String BASH_OPTION_C = "-c";
    private static final String CMD = "cmd";
    private static final String CMD_OPTION_C = "/c";

    private File workingDirectory;

    private List<String> arguments = new ArrayList<>();

    private ProcessListener[] shellListeners;

    /**
     * Default constructor.
     */
    public AbstractTerminalCommand() {
        super();
    }

    /**
     * Constructor initializes with working directory.
     * @param workingDirectory the working directory where the command is to be executed from
     * @param shellListeners
     */
    public AbstractTerminalCommand(File workingDirectory, ProcessListener... shellListeners) {
        this.workingDirectory = workingDirectory;
        this.shellListeners = shellListeners;
    }

    @Override
    public ProcessBuilder getProcessBuilder() {
        validateWorkingDirectory(workingDirectory);

        List<String> commands = new ArrayList<>();
        if (SystemUtils.IS_OS_UNIX) {
            commands.add(BASH);
            commands.add(BASH_OPTION_C);
        } else {
            commands.add(CMD);
            commands.add(CMD_OPTION_C);
        }

        commands.add(buildCommand());

        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.directory(workingDirectory);

        LOG.debug("Process builder commands: " + commands);
        return pb;
    }

    @Override
    public ProcessBuilder getShell() {
        validateWorkingDirectory(workingDirectory);

        List<String> commands = new ArrayList<>();
        if (SystemUtils.IS_OS_UNIX) {
            commands.add(BASH);
        } else {
            commands.add(CMD);
        }

        ProcessBuilder pb = new ProcessBuilder(commands);
        pb.directory(workingDirectory);
        return pb;
    }

    /**
     * Validate the working directory.
     * @param workingDirectory
     */
    protected void validateWorkingDirectory(File workingDirectory) {
        if (workingDirectory == null) {
            throw new IllegalStateException("Working directory has not been set");
        }

        if (!workingDirectory.isDirectory()) {
            throw new IllegalStateException(String.format("Invalid working directory '%s'", workingDirectory.getAbsolutePath()));
        }
    }

    /**
     * Gets the arguments.
     *
     * @return
     */
    public List<String> getArguments() {
        return arguments;
    }

    /**
     * Sets the workingDirectory.
     *
     * @param workingDirectory
     */
    public void setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * Gets the workingDirectory.
     *
     * @return
     */
    public File getWorkingDirectory() {
        return workingDirectory;
    }

    /**
     * Gets the shellListeners.
     *
     * @return
     */
    @Override
    public ProcessListener[] getShellListeners() {
        return shellListeners;
    }
}

