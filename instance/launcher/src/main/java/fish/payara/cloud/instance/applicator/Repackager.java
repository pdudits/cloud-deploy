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

import fish.payara.cloud.instance.Main;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class Repackager {
    private final CLIBootstrap bootstrap;

    public Repackager(CLIBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public void packageTo(String rootDir) throws Exception {
        var arguments = new ArrayList<String>();
        var root = Files.createDirectories(Path.of(rootDir));

        if (bootstrap != null) {
            arguments.add("--rootdir");
            arguments.add(rootDir);
            arguments.add("--outputlauncher");

            bootstrap.bootstrap(arguments.toArray(String[]::new));
        } else if (!Files.exists(root.resolve("launch-micro.jar"))) {
            throw new IllegalArgumentException(rootDir + " is not Payara Micro directory with version and no " +
                    "Micro is available on classpath");
        }

        try (var launchMicro = new JarFile(root.resolve("launch-micro.jar").toFile())) {
            var sourceAttr = launchMicro.getManifest().getMainAttributes();
            var targetManifest = new Manifest();
            targetManifest.getMainAttributes().putAll(sourceAttr);
            var targetAttr = targetManifest.getMainAttributes();
            targetAttr.putValue("Main-Class", Main.class.getName());
            targetAttr.putValue("Class-Path", "launch-micro.jar " + sourceAttr.getValue("Class-Path"));
            try (var cloudInstance = new JarOutputStream(
                    new FileOutputStream(root.resolve("cloud-instance.jar").toFile()), targetManifest);
                 var self = new JarFile(launcherJar().toFile())) {

                self.stream().filter(entry -> !entry.getName().equals("META-INF/MANIFEST.MF"))
                        .forEach(entry -> {
                            try {
                                cloudInstance.putNextEntry(entry);
                                try (InputStream input = self.getInputStream(entry)) {
                                    byte[] buffer = new byte[4096];
                                    for (int read = 0; (read = input.read(buffer)) > 0; ) {
                                        cloudInstance.write(buffer, 0, read);
                                    }
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
        }
    }

    private Path launcherJar() {
        var protectionDomain = Repackager.class.getProtectionDomain();
        CodeSource codeSource = protectionDomain.getCodeSource();
        if (codeSource != null) {
            try {
                return Path.of(codeSource.getLocation().toURI());
            } catch (URISyntaxException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Cannot determine jar of cloud launcher");
    }
}
