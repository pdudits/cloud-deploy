/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fish.payara.cloud.deployer.endpoints;

import fish.payara.cloud.deployer.process.ConfigBean;
import fish.payara.cloud.deployer.process.DeploymentProcessState;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Model;
import javax.enterprise.inject.Produces;

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

    @Produces @Model
    public DeploymentProcessState getDeployment() {
        return deployment;
    }

    public void setDeployment(DeploymentProcessState deployment) {
        this.deployment = deployment;
    }

    @Produces @Model
    public ConfigBean getConfig() {
        return deployment == null ? null : ConfigBean.forDeploymentProcess(deployment);
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
}
