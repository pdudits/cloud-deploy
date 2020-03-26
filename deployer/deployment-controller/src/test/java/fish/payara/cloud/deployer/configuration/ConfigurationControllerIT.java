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

package fish.payara.cloud.deployer.configuration;

import fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration;
import fish.payara.cloud.deployer.inspection.datasource.DatasourceConfiguration;
import fish.payara.cloud.deployer.inspection.mpconfig.MicroprofileConfiguration;
import fish.payara.cloud.deployer.process.ChangeKind;
import fish.payara.cloud.deployer.process.Configuration;
import fish.payara.cloud.deployer.process.ConfigurationFactory;
import fish.payara.cloud.deployer.process.DeploymentProcess;
import fish.payara.cloud.deployer.process.Namespace;
import fish.payara.cloud.deployer.process.PersistedDeployment;
import fish.payara.cloud.deployer.process.ProcessObserver;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import static fish.payara.cloud.deployer.ArquillianDeployments.assertj;
import static fish.payara.cloud.deployer.ArquillianDeployments.compose;
import static fish.payara.cloud.deployer.ArquillianDeployments.configuration;
import static fish.payara.cloud.deployer.ArquillianDeployments.shrinkwrap;
import static fish.payara.cloud.deployer.inspection.InspectionHelper.write;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

@RunWith(Arquillian.class)
public class ConfigurationControllerIT {

    @Deployment
    public static WebArchive createDeployment() {
        return compose(shrinkwrap(), configuration(), assertj());
    }

    @Inject
    DeploymentProcess deployment;

    @Inject
    ProcessObserver observer;

    @Inject
    ConfigurationFactory factory;

    @Test
    public void configurationIsStartedAfterInspection() {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .addAsManifestResource(new StringAsset("artifactId=maven-artifact"), "maven/fish.payara.cloud.test/test/pom.properties"));
        observer.reset();
        var process = deployment.start(testFile, "test-app.war", new Namespace("test","dev"));
        observer.await(ChangeKind.CONFIGURATION_STARTED);
        deployment.submitConfigurations(process);
        observer.await(ChangeKind.CONFIGURATION_FINISHED);
    }
    
    @Test
    public void configurationIsAutomaticallySubmittedWhenUsingDefaults() {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .addAsManifestResource(new StringAsset("artifactId=maven-artifact"), "maven/fish.payara.cloud.test/test/pom.properties"));
        observer.reset();
        var process = deployment.startWithDefaultConfiguration(testFile, "test-app.war", new Namespace("test","dev"));
        observer.await(ChangeKind.CONFIGURATION_STARTED);
        observer.await(ChangeKind.CONFIGURATION_FINISHED);
    }

    // these mainly exercise configuration factory. Semantic tests are better suited for unit tests of respective
    // configurations
    @Test
    public void allConfigurationKindsAreImportableWithoutDefaults() {
        for (ImportableConfigs imported : ImportableConfigs.values()) {
            var config = factory.importConfiguration(imported.nullDefaults());
            assertThat(config).isInstanceOf(imported.configClass);
        }
    }

    @Test
    public void allConfigurationKindsAreImportableWithoEmptyDefaults() {
        for (ImportableConfigs imported : ImportableConfigs.values()) {
            var config = factory.importConfiguration(imported.emptyDefaults());
            assertThat(config).isInstanceOf(imported.configClass);
        }
    }


    enum ImportableConfigs {
        CONTEXT_ROOT(ContextRootConfiguration.KIND, ContextRootConfiguration.class),
        MP_CONFIG(MicroprofileConfiguration.KIND, MicroprofileConfiguration.class),
        DATASOURCE(DatasourceConfiguration.KIND, DatasourceConfiguration.class);

        final String kind;
        final Class<? extends Configuration> configClass;

        ImportableConfigs(String kind, Class<? extends Configuration> configClass) {
            this.kind = kind;
            this.configClass = configClass;
        }

        ImportedConfiguration nullDefaults() {
            return new ImportedConfiguration(kind, "any", null);
        }

        ImportedConfiguration emptyDefaults() {
            return new ImportedConfiguration(kind, "any", Collections.emptyMap());
        }
    }

    static class ImportedConfiguration implements PersistedDeployment.PersistedConfiguration {
        private String kind;
        private String id;
        private Map<String,String> defaultValues;
        private Map<String,String> values;

        public ImportedConfiguration(String kind, String id, Map<String, String> defaultValues) {
            this.kind = kind;
            this.id = id;
            this.defaultValues = defaultValues;
        }

        @Override
        public String getKind() {
            return kind;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Map<String, String> getDefaultValues() {
            return defaultValues;
        }

        @Override
        public Map<String, String> getValues() {
            return values;
        }
    }
}
