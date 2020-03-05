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

import fish.payara.cloud.instance.applicator.CLIApplicator;
import fish.payara.cloud.instance.applicator.CLIBootstrap;
import fish.payara.cloud.instance.applicator.Repackager;
import fish.payara.micro.boot.PayaraMicroBoot;
import fish.payara.micro.boot.PayaraMicroLauncher;

/**
 * Apply configuration steps to Payara Micro instance.
 *
 * <p>Based on classpath constellation, different strategies need to be taken.</p>
 */
public interface Applicator {
    /**
     * Get applicator fit for current launch mode
     * @return an instance
     */
    static Applicator getInstance() {
        // API has limited configuration options, chiefly there's no access
        // to preboot/postboot commands
        //PayaraMicro.unpackJars();
        //PayaraMicro.setUpackedJarDir();
        //PayaraMicro instance = PayaraMicro.getInstance();

        // Payara micro launcher gives us commands via command line arguments
        CLIBootstrap bootstrap = PayaraMicroLauncher::create;

        try {
            var rootLauncher = Class.forName("fish.payara.micro.impl.RootDirLauncher");
            var impl = Class.forName("fish.payara.micro.impl.PayaraMicroImpl");

            // if these classes are on our classpath, that means we're in flat classloader and should
            // utilize root launcher

            var mainMethod = rootLauncher.getMethod("main", String[].class);
            var instanceMethod = impl.getMethod("getInstance");

            bootstrap = (args) -> {
                mainMethod.invoke(null, (Object) args);
                return (PayaraMicroBoot) instanceMethod.invoke(null);
            };
        } catch (ClassNotFoundException e) {
            // we're not in flat classpath, previous bootstrap will work
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Payara Micro classes are not what I expect", e);
        }
        return new CLIApplicator(bootstrap);
    }

    static Repackager getRepackager() {
        try {
            // is there Micro on the classpath?
            Class.forName("fish.payara.micro.boot.PayaraMicroLauncher");
            return new Repackager(PayaraMicroLauncher::create);
        } catch (ClassNotFoundException e) {
            return new Repackager(null);
        }
    }

    void addPreBootCommand(String command, String... arguments);

    void addPostBootCommand(String command, String... arguments);

    void addPostDeployCommand(String command, String... arguments);

    default void addCommandlineArgument(String argument) {
        addCommandlineArgument(argument, null);
    }

    default void addCommandlineArgument(String argument, String value) {
        switch (argument) {
            case "--deploy":
            case "--deploymentdir":
            case "--systemproperties":
            case "--prebootcommandfile":
            case "--postbootcommandfile":
            case "--postdeploycommandfile":
                throw new IllegalArgumentException("Use specialized command for "+argument);
        }
    }

    void addDeployment(String artifact, String contextPath);

    default void addDeployment(String artifact) {
        addDeployment(artifact, null);
    }

    void addSystemProperty(String name, String value);

    PayaraMicroBoot start() throws Exception;

    PayaraMicroBoot getServer();

}
