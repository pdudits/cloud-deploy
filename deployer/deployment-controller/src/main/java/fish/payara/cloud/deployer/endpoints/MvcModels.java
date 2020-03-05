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
package fish.payara.cloud.deployer.endpoints;

import fish.payara.cloud.deployer.process.ConfigBean;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import fish.payara.cloud.deployer.process.Namespace;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Optional;
import javax.enterprise.context.Dependent;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Model;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

/**
 *
 * @author patrik
 */
@RequestScoped
class MvcModels {
    private DeploymentProcessState deployment;
    private ConfigBean config;
    private String configKind;
    private String configId;
    private Namespace namespace;

    @Produces @Model
    public DeploymentProcessState getDeployment() {
        return deployment;
    }

    public void setDeployment(DeploymentProcessState deployment) {
        this.deployment = deployment;
    }
    
    @Produces @Named @Dependent
    public Namespace getNamespace() {
        if (namespace != null) {
            return namespace;
        }
        if (deployment != null) {
            return deployment.getNamespace();
        }
        return null;
    }
    
    public void setNamespace(Namespace namespace) {
        this.namespace = namespace;
    }

    @Produces @Named @Dependent
    public ConfigBean getConfig() {
        if (config != null) {
            return config;
        }
        if (deployment != null) {
            return ConfigBean.forDeploymentProcess(deployment);
        }
        return null;
    }

    @Produces @Model
    public String getConfigKind() {
        return configKind;
    }

    public void setConfigKind(String configKind) {
        this.configKind = configKind;
    }

    @Produces @Model
    public String getConfigId() {
        return configId;
    }

    public void setConfigId(String configId) {
        this.configId = configId;
    }
    
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault());
    
    @Produces @Model
    public String lastStateTimestamp() {
        return deployment == null || deployment.getLastStatusChange() == null ? null : SHORT_TIME.format(deployment.getLastStatusChange());
    }

    void setConfig(ConfigBean configBean) {
        config = configBean;
    }
}
