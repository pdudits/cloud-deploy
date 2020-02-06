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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.json.bind.annotation.JsonbProperty;

/**
 * Frontend-friendly representation of set of configuration objects
 * @author jonathan
 */
public class ConfigBean {
    
    @JsonbProperty("deployment")
    String deploymentId;
    
    List<ConfigId> order;
    Map<String, Map<String, Config>> kind;

    public static ConfigBean forConfiguration(Set<Configuration> configurationSet) {
        return forConfiguration(configurationSet, null);
    }


    public static ConfigBean forConfiguration(Set<Configuration> configurationSet, String deploymentId) {
        ConfigBean configBean = new ConfigBean();
        configBean.setDeploymentId(deploymentId);
        for (Configuration config : configurationSet) {
            configBean.addConfig(config);
        }
        return configBean;
    }

    public static ConfigBean forDeploymentProcess(DeploymentProcessState state) {
        return forConfiguration(state.getConfigurations(), state.getId());
    }

    public List<ConfigId> getOrder() {
        return order;
    }

    public void setOrder(List<ConfigId> order) {
        this.order = order;
    }

    public Map<String, Map<String, Config>> getKind() {
        return kind;
    }

    public void setKind(Map<String, Map<String, Config>> kind) {
        this.kind = kind;
    }
    
    public ConfigBean() {
        order = new ArrayList<>();
        kind = new HashMap<>();
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }
    
    public void addConfig(Configuration config) {
        ConfigId id = new ConfigId(config.getKind(), config.getId());
        order.add(id);
        Map<String, Config> configKind = kind.get(config.getKind());
        if (configKind == null) {
            configKind = new HashMap<>();
            kind.put(config.getKind(), configKind);
        }
        Config acutalConfig = new Config();
        for (String key : config.getKeys()) {
            Key keyDetails = new Key();
            keyDetails.setName(key);
            keyDetails.setRequired(config.isRequired(key));
            keyDetails.setDefaultValue(config.getDefaultValue(key));
            acutalConfig.keydetails.add(keyDetails);
            Optional<String> value = config.getValue(key);
            if (value.isPresent()) {
                acutalConfig.values.put(key, config.getValue(key).get());
            }
        }
        configKind.put(config.getId(), acutalConfig);
    }
    
    
    public static class ConfigId {
        String kind;
        String id;
        
        public ConfigId() {}
        public ConfigId(String kind, String id) {
            this.kind = kind;
            this.id = id;
        }

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
        
        
    }

    public static class Config {

        @JsonbProperty("keys")
        List<Key> keydetails = new ArrayList<>();
        Map<String, String> values = new HashMap<>();

        public List<Key> getKeydetails() {
            return keydetails;
        }

        public void setKeydetails(List<Key> keydetails) {
            this.keydetails = keydetails;
        }

        public Map<String, String> getValues() {
            return values;
        }

        public void setValues(Map<String, String> values) {
            this.values = values;
        }

    }
    
    public static class Key {
        String name;
        boolean required;
        @JsonbProperty("default")
        Optional<String> defaultValue;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public Optional<String> getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(Optional<String> defaultValue) {
            this.defaultValue = defaultValue;
        }
    }
    
}
