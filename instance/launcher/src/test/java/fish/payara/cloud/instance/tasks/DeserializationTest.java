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

package fish.payara.cloud.instance.tasks;


import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeserializationTest {

    static final Path root = Path.of("src/test/payloads/");

    private <T extends ConfigurationTask> T deserialize(String file, Class<T> resultClass) {
        var result = Serialization.deserialize(root.resolve(file));
        assertEquals(resultClass, result.getClass());
        return resultClass.cast(result);
    }

    private String fileName(String relativePath) {
        return root.resolve(relativePath).toAbsolutePath().toString();
    }

    @Test
    public void simpleDeployment() {
        var value = deserialize("deployment-constants.json", Deployment.class);
        assertEquals("/", value.getContextRoot().value());
        assertEquals(fileName("test.war"), value.getArtifact().fileName());
    }

    @Test
    public void fileReference() {
        // context root is in contents of file
        var value = deserialize("deployment-filereference.json", Deployment.class);
        assertEquals("indirectValue", value.getContextRoot().value());
    }

    @Test
    public void propertyReference() {
        // context root is a value in a property file
        var value = deserialize("deployment-propertyreference.json", Deployment.class);
        assertEquals("propertyValue", value.getContextRoot().value());
    }

    @Test
    public void fileIndirectReference() {
        // context root is in file listed as a value of a property file
        var value = deserialize("deployment-complexreference.json", Deployment.class);
        assertEquals("indirectValue", value.getContextRoot().value());
    }

    @Test
    public void mpConfigFile() {
        var value = deserialize("mp-config-file.json", MicroprofileConfigProperties.class);
        assertEquals(fileName("valueInFile"), value.getFile().fileName());
    }

    @Test
    public void mpConfigMap() {
        var value = deserialize("mp-config-map.json", MicroprofileConfigProperties.class);
        var map = value.getMap();
        assertEquals("constantValue", map.get("constant").value());
        assertEquals("valueInFile", map.get("fileRef").value());
        assertEquals("propertyValue", map.get("propertyRef").value());
    }

    @Test
    public void datasourceConstants() {
        var value = deserialize("datasource-constants.json", DataSource.class);
        assertEquals("2", value.getSteadyPoolSize().value());
        var definition = value.toDefinition();
        // we (deliberately) missed jndiName, so this represents default data source
        assertTrue(definition.isDefaultDataSource());
    }

}
