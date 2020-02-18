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

package fish.payara.cloud.deployer.inspection.mpconfig;

import fish.payara.cloud.deployer.inspection.InspectedArtifact;
import fish.payara.cloud.deployer.process.ProcessAccessor;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipFile;

import static fish.payara.cloud.deployer.inspection.InspectionHelper.write;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MicroprofileConfigInspectionTest {
    @Test
    public void noConfigFoundWhenNoPropertyFile() throws IOException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .add(new StringAsset("<html></html>"), "index.html"));
        assertNull(discoveredConfig("empty", testFile));
    }

    @Test
    public void configFoundWhenPropertyPresent() throws IOException {
        var testFile = write(ShrinkWrap.create(WebArchive.class)
                .add(new StringAsset("<html></html>"), "index.html")
                .addAsResource(new StringAsset("prop.1=one\nprop/second=2\nempty="), "META-INF/microprofile-config.properties"));
        var config = discoveredConfig("configured", testFile);
        assertNotNull(config);
        assertTrue("All keys from file should be discovered", config.getKeys().containsAll(Set.of("prop.1", "prop/second")));
        assertEquals("one", config.getValue("prop.1").get());
        assertEquals("2", config.getValue("prop/second").get());
        assertEquals("", config.getValue("empty").get());
        // let's try updates as well

        ProcessAccessor.updateConfiguration(config, Map.of("prop.1","ONE", "other", "O"));

        assertEquals("ONE", config.getValue("prop.1").get());
        assertEquals("one", config.getDefaultValue("prop.1").get());
        assertEquals("O", config.getValue("other").get());
        assertFalse(config.getDefaultValue("other").isPresent());
    }

    private MicroprofileConfiguration discoveredConfig(String name, File testFile) throws IOException {
        var inspection = new MicroprofileConfigInspection();
        var artifact = InspectedArtifact.inspect(name, new ZipFile(testFile), List.of(inspection));
        var config = artifact.getConfigurations().stream().filter(c -> c.getKind().equals(MicroprofileConfiguration.KIND))
                .map(MicroprofileConfiguration.class::cast).findAny().orElse(null);
        return config;
    }
}
