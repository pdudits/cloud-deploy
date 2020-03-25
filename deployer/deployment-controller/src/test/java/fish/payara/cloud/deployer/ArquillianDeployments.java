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

package fish.payara.cloud.deployer;

import fish.payara.cloud.deployer.artifactstorage.ArtifactStorage;
import fish.payara.cloud.deployer.artifactstorage.TempArtifactStorage;
import fish.payara.cloud.deployer.endpoints.Application;
import fish.payara.cloud.deployer.inspection.Inspection;
import fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration;
import fish.payara.cloud.deployer.inspection.datasource.DatasourceConfiguration;
import fish.payara.cloud.deployer.inspection.mpconfig.MicroprofileConfiguration;
import fish.payara.cloud.deployer.process.ChangeKind;
import fish.payara.cloud.deployer.process.Configuration;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.DeploymentProcessLogOutput;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.Namespace;
import fish.payara.cloud.deployer.process.ProcessAccessor;
import fish.payara.cloud.deployer.process.StateChanged;
import fish.payara.cloud.deployer.provisioning.Provisioner;
import fish.payara.cloud.deployer.setup.SetupExtension;
import fish.payara.cloud.deployer.utils.DurationConverter;
import org.eclipse.microprofile.config.spi.Converter;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

import javax.enterprise.inject.spi.Extension;
import javax.mvc.Models;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;

public class ArquillianDeployments {
    public interface Composition {
        WebArchive apply(WebArchive source);
    }

    public static WebArchive compose(Composition... parts) {
        var archive = ShrinkWrap.create(WebArchive.class);
        return compose(archive, parts);
    }

    private static WebArchive compose(WebArchive archive, Composition... parts) {
        for (Composition part : parts) {
            archive = part.apply(archive);
        }
        return archive;
    }

    public static Composition azureStorage() {
        var shrinkwrap = Maven.resolver().loadPomFromFile("pom.xml").resolve("com.microsoft.azure:azure-storage")
                .withTransitivity().asFile();
        return a -> a.addAsLibraries(shrinkwrap)
                .addClass(ArtifactStorage.class)
                .addClass("fish.payara.cloud.deployer.artifactstorage.AzureBlobStorage");
    }

    public static Composition mpConfig(Map<String,String> properties) {
        var props = new Properties();
        props.putAll(properties);
        var writer = new StringWriter();
        try {
            props.store(writer, null);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible!", e);
        }
        return a -> a.addAsResource(new StringAsset(writer.toString()), "META-INF/microprofile-config.properties");
    }

    public static Composition deploymentProcessModel() {
        return a -> a.addClasses(DeploymentProcessState.class,
                Namespace.class,
                ChangeKind.class,
                StateChanged.class,
                DeploymentProcessLogOutput.class,
                Configuration.class,
                ProcessAccessor.class);
    }

    public static Composition setupExtension() {
        return a -> a.addAsServiceProvider(Extension.class, SetupExtension.class);
    }

    public static Composition deploymentProcess() {
        return a -> a.addPackage(DeploymentProcess.class.getPackage());
    }

    public static Composition utils() {
        return a -> a.addAsServiceProvider(Converter.class, DurationConverter.class)
                    .addPackage(DurationConverter.class.getPackage());
    }

    public static Composition inspection() {
        return a -> compose(a, deploymentProcess(), utils())
                        .addPackage(Inspection.class.getPackage())
                        .addPackage(ContextRootConfiguration.class.getPackage())
                        .addPackage(DatasourceConfiguration.class.getPackage())
                        .addPackage(MicroprofileConfiguration.class.getPackage());
    }

    public static Composition shrinkwrap() {
        var shrinkwrap = Maven.resolver().loadPomFromFile("pom.xml").resolve("org.jboss.shrinkwrap:shrinkwrap-impl-base")
                .withTransitivity().asFile();
        return a -> a.addAsLibraries(shrinkwrap);
    }

    public static Composition provisioning() {
        return a -> compose(a, deploymentProcess(), utils()).addPackage(Provisioner.class.getPackage())
                .addClass(ArtifactStorage.class)
                .addClass(TempArtifactStorage.class);
    }

    public static Composition configuration() {
        return a -> compose(a, deploymentProcess(), inspection())
                .addPackage("fish.payara.cloud.deployer.configuration");
    }

    public static Composition restApi() {
        return a -> compose(a, deploymentProcess(), inspection(), provisioning(), configuration())
                .addPackage(Application.class.getPackage())
                .addClass(Models.class);
    }

}
