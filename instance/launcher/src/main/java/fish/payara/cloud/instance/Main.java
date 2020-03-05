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

package fish.payara.cloud.instance;

import fish.payara.cloud.instance.tasks.ConfigurationTask;
import fish.payara.cloud.instance.tasks.Serialization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Entry point.
 * Invoke with path to directory containing configuration tasks in json format.
 */
public class Main {
    private final Path configRoot;

    public Main(String configDir) {
        this.configRoot = Path.of(configDir);
    }

    public static void main(String[] args) throws Exception {
        if ("--outputlauncher".equals(args[0])) {
            Applicator.getRepackager().packageTo(args[1]);
        } else {
            Main main = new Main(args[0]);
            main.run();
        }
    }

    private void run() throws Exception {
        List<ConfigurationTask> tasks = parseConfig();
        ConfigurationPlan plan = ConfigurationPlan.plan(tasks);
        Applicator planApplicator = Applicator.getInstance();
        plan.execute(planApplicator);
        planApplicator.start();
    }

    private List<ConfigurationTask> parseConfig() throws IOException {
        return Files.walk(configRoot)
                .filter(p -> p.getFileName().toString().endsWith(".json"))
                .map(Serialization::deserialize).collect(Collectors.toList());
    }


}
