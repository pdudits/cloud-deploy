/*
 * Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
 *
 *  The contents of this file are subject to the terms of either the GNU
 *  General Public License Version 2 only ("GPL") or the Common Development
 *  and Distribution License("CDDL") (collectively, the "License").  You
 *  may not use this file except in compliance with the License.  You can
 *  obtain a copy of the License at
 *  https://github.com/payara/Payara/blob/master/LICENSE.txt
 *  See the License for the specific
 *  language governing permissions and limitations under the License.
 *
 *  When distributing the software, include this License Header Notice in each
 *  file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *  GPL Classpath Exception:
 *  The Payara Foundation designates this particular file as subject to the "Classpath"
 *  exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 *  file that accompanied this code.
 *
 *  Modifications:
 *  If applicable, add the following below the License Header, with the fields
 *  enclosed by brackets [] replaced by your own identifying information:
 *  "Portions Copyright [year] [name of copyright owner]"
 *
 *  Contributor(s):
 *  If you wish your version of this file to be governed by only the CDDL or
 *  only the GPL Version 2, indicate your decision by adding "[Contributor]
 *  elects to include this software in this distribution under the [CDDL or GPL
 *  Version 2] license."  If you don't indicate a single choice of license, a
 *  recipient has the option to distribute your version of this file under
 *  either the CDDL, the GPL Version 2 or to extend the choice of license to
 *  its licensees as provided above.  However, if you add GPL Version 2 code
 *  and therefore, elected the GPL Version 2 license, then the option applies
 *  only if the new code is made subject to such option by the copyright
 *  holder.
 */

package fish.payara.cloud.instance.applicator;

import fish.payara.cloud.instance.Applicator;
import fish.payara.micro.boot.PayaraMicroBoot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.util.stream.Collectors.joining;

public class CLIApplicator implements Applicator  {
    private final CLIBootstrap bootstrap;

    private List<Command> prebootCommands = new ArrayList<>();
    private List<Command> postBootCommands = new ArrayList<>();
    private List<Command> postDeployCommands = new ArrayList<>();
    private List<Command> postBootstrapCommands = new ArrayList<>();

    private Map<String,String> systemProperties = new HashMap<>();
    private Map<String,String> deployments = new LinkedHashMap<>();
    private List<String> arguments = new ArrayList<>();

    static class Command {
        List<String> contents;

        public Command(String command, String ...arguments) {
            contents = new ArrayList<>(arguments.length+1);
            contents.add(command);
            contents.addAll(Arrays.asList(arguments));
        }
    }

    public CLIApplicator(CLIBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public void addPreBootCommand(String command, String... arguments) {
        prebootCommands.add(new Command(command, arguments));
    }

    @Override
    public void addPostBootCommand(String command, String... arguments) {
        postBootCommands.add(new Command(command, arguments));
    }

    @Override
    public void addPostDeployCommand(String command, String... arguments) {
        postDeployCommands.add(new Command(command, arguments));
    }

    @Override
    public void addCommandlineArgument(String argument, String value) {
        Applicator.super.addCommandlineArgument(argument, value);
        arguments.add(argument);
        if (value != null) {
            arguments.add(value);
        }
    }

    @Override
    public void addDeployment(String artifact, String contextPath) {
        deployments.put(artifact, contextPath);
    }

    @Override
    public void addSystemProperty(String name, String value) {
        systemProperties.put(name, value);
    }

    @Override
    public PayaraMicroBoot start() throws Exception {
        List<String> completeArguments = new ArrayList<>();
        completeArguments.addAll(renderPreboot());
        completeArguments.addAll(renderPostboot());
        completeArguments.addAll(renderPostdeploy());
        completeArguments.addAll(renderSystemProperties());
        completeArguments.addAll(renderDeployments());
        completeArguments.addAll(arguments);
        return bootstrap.bootstrap(completeArguments.toArray(String[]::new));
    }

    private Collection<String> renderPreboot() {
        return renderScript("--prebootcommandfile", prebootCommands);
    }

    private Collection<String> renderPostboot() {
        return renderScript("--postbootcommandfile", postBootCommands);
    }

    private Collection<String> renderPostdeploy() {
        return renderScript("--postdeploycommandfile", postDeployCommands);
    }

    private Collection<String> renderSystemProperties() {
        if (systemProperties.isEmpty()) {
            return Collections.emptyList();
        }
        try (var file = new TempFile("systemprops")) {
            var props = new Properties();
            props.putAll(systemProperties);
            props.list(file.writer);
            return List.of("--systemproperties", file.filename());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write temp file", e);
        }
    }

    private Collection<String> renderDeployments() {
        var result = new ArrayList<String>();
        for (Map.Entry<String, String> deployment : deployments.entrySet()) {
            result.add("--deploy");
            if (deployment.getValue() == null) {
                result.add(deployment.getKey());
            } else {
                result.add(deployment.getKey()+File.pathSeparator+deployment.getValue());
            }
        }
        return result;
    }

    private Collection<String> renderScript(String argument, List<Command> commands) {
        if (commands.isEmpty()) {
            return Collections.emptyList();
        }
        try (var file = new TempFile(argument.replaceAll("--",""))) {
            commands.forEach(c -> file.println(c.contents.stream().collect(joining(" "))));
            return List.of(argument, file.filename());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot write temp file", e);
        }
    }


    static class TempFile implements AutoCloseable {
        private final File file;
        private final PrintWriter writer;

        TempFile(String prefix) throws IOException {
            file = File.createTempFile(prefix, "");
            writer = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8));
        }

        void println(String s) {
            writer.println(s);
        }

        String filename() {
            return file.getAbsolutePath();
        }

        @Override
        public void close() {
            writer.close();
        }
    }

    @Override
    public PayaraMicroBoot getServer() {
        return null;
    }

}

