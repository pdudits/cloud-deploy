/*
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 *  Copyright (c) [2020] Payara Foundation and/or its affiliates. All rights reserved.
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
 *  file and include the License.
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
package fish.payara.cloud.deployer.process;

import fish.payara.cloud.deployer.process.ConfigurationSerializer;
import fish.payara.cloud.deployer.inspection.contextroot.ContextRootConfiguration;
import fish.payara.cloud.deployer.process.Configuration;
import fish.payara.cloud.deployer.process.ProcessAccessor;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import org.junit.Assert;
import org.junit.Test;

/**
 * 
 * @author jonathan coustick
 */
public class SerilizationTest {
    
    private static final String CONTEXT_START = "{\"TESTMODULE\":{\"keys\":[{\"name\"";
    private static final String CONTEXT_KEYS_1 = "{\"name\":\"context-root\",\"required\":false,\"default\":\"TEXTCONTEXT\"}";
    private static final String CONTEXT_KEYS_2 = "{\"name\":\"app-name\",\"required\":false,\"default\":\"TESTAPP\"}";
    private static final String CONTEXT_VALUES = "\"values\":{\"context-root\":\"TEXTCONTEXT\",\"app-name\":\"TESTAPP\"}}}";
    
    private static final String SIMPLE_START = "{\"TESTID\":{\"keys\":[{\"name\":";
    private static final String SIMPLE_KEYS_1 = "{\"name\":\"key1\",\"required\":false,\"default\":\"DEFAULT\"}";
    private static final String SIMPLE_KEYS_2 = "{\"name\":\"key2\",\"required\":false,\"default\":\"DEFAULT\"}";
    private static final String SIMPLE_KEYS_3 = "{\"name\":\"key3\",\"required\":false,\"default\":\"DEFAULT\"}";
    private static final String SIMPLE_VALUES = "\"values\":{\"key1\":\"Value1\",\"key2\":\"value2\",\"key3\":\"value3\"}}}";
    
    @Test
    public void contextRootconfigTest() {
        Configuration config = new ContextRootConfiguration("TESTMODULE", "TESTAPP", "TEXTCONTEXT");
        ConfigurationSerializer serialiser = new ConfigurationSerializer();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try (JsonGenerator generator = Json.createGenerator(stream)) {
            serialiser.serialize(config, generator, null);
        }
        String result = stream.toString();
        System.out.println(result);
        Assert.assertTrue(result.contains(CONTEXT_START));
        Assert.assertTrue(result.contains(CONTEXT_KEYS_1));
        Assert.assertTrue(result.contains(CONTEXT_KEYS_2));
        Assert.assertTrue(result.contains(CONTEXT_VALUES));
    }
    
    @Test
    public void simpleConfigTest() {
        Configuration config = new SimpleConfiguration();
        ConfigurationSerializer serialiser = new ConfigurationSerializer();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try (JsonGenerator generator = Json.createGenerator(stream)) {
            serialiser.serialize(config, generator, null);
        }
        String result = stream.toString();
        System.out.println(result);
        Assert.assertTrue(result.contains(SIMPLE_START));
        Assert.assertTrue(result.contains(SIMPLE_KEYS_1));
        Assert.assertTrue(result.contains(SIMPLE_KEYS_2));
        Assert.assertTrue(result.contains(SIMPLE_KEYS_3));
        Assert.assertTrue(result.contains(SIMPLE_VALUES));
    }
    
    private class SimpleConfiguration extends Configuration {
        
        Map<String, String> MAP = Map.of("key1", "Value1", "key2", "value2", "key3", "value3");
        
        public SimpleConfiguration() {
            super("TESTID");
            ProcessAccessor.updateConfiguration(this, MAP);
        }

        @Override
        public String getKind() {
            return "TEST";
        }

        @Override
        public Set<String> getKeys() {
            return MAP.keySet();
        }

        @Override
        public Optional<String> getDefaultValue(String key) {
            return Optional.of("DEFAULT");
        }
    
    }
    
    
}
