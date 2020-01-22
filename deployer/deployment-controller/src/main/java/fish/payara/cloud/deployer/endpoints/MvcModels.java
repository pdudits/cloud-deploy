/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fish.payara.cloud.deployer.endpoints;

import fish.payara.cloud.deployer.process.DeploymentProcessState;
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

    @Produces @Model
    public DeploymentProcessState getDeployment() {
        return deployment;
    }

    public void setDeployment(DeploymentProcessState deployment) {
        this.deployment = deployment;
    }
    
    
}
