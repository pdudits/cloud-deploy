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
import fish.payara.cloud.deployer.process.Configuration;
import fish.payara.cloud.deployer.process.PersistedDeployment;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * WebAppSpecConfiguration
 */
public class WebAppSpecConfiguration {
    private String kind;
    private String id;
    private Map<String, String> values;
    private Map<String, String> defaultValues;


    public WebAppSpecConfiguration kind(String kind) {

        this.kind = kind;
        return this;
    }

    public String getKind() {
        return kind;
    }


    public void setKind(String kind) {
        this.kind = kind;
    }

    public WebAppSpecConfiguration id(String id) {

        this.id = id;
        return this;
    }

    public String getId() {
        return id;
    }


    public void setId(String id) {
        this.id = id;
    }


    public WebAppSpecConfiguration values(Map<String, String> values) {
        this.values = values;
        return this;
    }

    public WebAppSpecConfiguration putValuesItem(String key, String valuesItem) {
        if (this.values == null) {
            this.values = new HashMap<>();
        }
        this.values.put(key, valuesItem);
        return this;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> getValues() {
        return values;
    }


    public void setValues(Map<String, String> values) {
        this.values = values;
    }


    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> getDefaultValues() {
        return defaultValues;
    }

    public void setDefaultValues(Map<String, String> defaultValues) {
        this.defaultValues = defaultValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WebAppSpecConfiguration webAppSpecConfiguration = (WebAppSpecConfiguration) o;
        return Objects.equals(this.kind, webAppSpecConfiguration.kind) &&
                Objects.equals(this.id, webAppSpecConfiguration.id) &&
                Objects.equals(this.values, webAppSpecConfiguration.values) &&
                Objects.equals(this.defaultValues, webAppSpecConfiguration.defaultValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, id);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class WebAppSpecConfiguration {\n");
        sb.append("    kind: ").append(toIndentedString(kind)).append("\n");
        sb.append("    id: ").append(toIndentedString(id)).append("\n");
        sb.append("    values: ").append(toIndentedString(values)).append("\n");
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

    public PersistedDeployment.PersistedConfiguration asPersistedConfiguration() {
        return new PersistedDeployment.PersistedConfiguration() {
            @Override
            public String getKind() {
                return WebAppSpecConfiguration.this.getKind();
            }

            @Override
            public String getId() {
                return WebAppSpecConfiguration.this.getId();
            }

            @Override
            public Map<String, String> getValues() {
                return WebAppSpecConfiguration.this.getValues();
            }

            @Override
            public Map<String, String> getDefaultValues() {
                return WebAppSpecConfiguration.this.getDefaultValues();
            }
        };
    }

}

