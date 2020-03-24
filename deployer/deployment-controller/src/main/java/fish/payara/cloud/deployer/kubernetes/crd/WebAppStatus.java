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


package fish.payara.cloud.deployer.kubernetes.crd;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * WebAppStatus
 */
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2020-03-23T19:23:58.881+01:00[Europe/Prague]")
public class WebAppStatus {
    private Long observedGeneration;

    private URI publicEndpoint;

    private List<WebAppStatusConditions> conditions = null;


    public WebAppStatus observedGeneration(Long observedGeneration) {

        this.observedGeneration = observedGeneration;
        return this;
    }

    public Long getObservedGeneration() {
        return observedGeneration;
    }


    public void setObservedGeneration(Long observedGeneration) {
        this.observedGeneration = observedGeneration;
    }


    public WebAppStatus publicEndpoint(URI publicEndpoint) {

        this.publicEndpoint = publicEndpoint;
        return this;
    }

    public URI getPublicEndpoint() {
        return publicEndpoint;
    }


    public void setPublicEndpoint(URI publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public WebAppStatus conditions(List<WebAppStatusConditions> conditions) {

        this.conditions = conditions;
        return this;
    }

    public WebAppStatus addConditionsItem(WebAppStatusConditions conditionsItem) {
        if (this.conditions == null) {
            this.conditions = new ArrayList<>();
        }
        this.conditions.add(conditionsItem);
        return this;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<WebAppStatusConditions> getConditions() {
        return conditions;
    }


    public void setConditions(List<WebAppStatusConditions> conditions) {
        this.conditions = conditions;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WebAppStatus webAppStatus = (WebAppStatus) o;
        return Objects.equals(this.observedGeneration, webAppStatus.observedGeneration) &&
                Objects.equals(this.publicEndpoint, webAppStatus.publicEndpoint) &&
                Objects.equals(this.conditions, webAppStatus.conditions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(observedGeneration, publicEndpoint, conditions);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class WebAppStatus {\n");
        sb.append("    observedGeneration: ").append(toIndentedString(observedGeneration)).append("\n");
        sb.append("    publicEndpoint: ").append(toIndentedString(publicEndpoint)).append("\n");
        sb.append("    conditions: ").append(toIndentedString(conditions)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }

}

