/*
 * Copyright (c) 2019 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.cloud.deployer.process;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base class for deployment configuration.
 * Implementations represent concrete facets of deployment, like database configuration, path mapping or Microprofile
 * Config values.
 */
public abstract class Configuration {
    private final String id;
    private boolean submitted;
    private final Map<String,String> updatedValues = new HashMap<>();

    protected Configuration(String id) {
        this.id = id;
    }

    /**
     * Kind of configuration. Kind usually represents semantic of the individual configuration keys.
     * @return
     */
    public abstract String getKind();

    public final String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Configuration that = (Configuration) o;
        return getId().equals(that.getId()) &&
                getKind().equals(that.getKind());
    }

    @Override
    public String toString() {
        return "Configuration "+getKind()+"/"+getId();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getKind());
    }

    public abstract Set<String> getKeys();

    public boolean isRequired(String key) {
        return false;
    }

    public Optional<String> getDefaultValue(String key) {
        return Optional.empty();
    }

    public Optional<String> getValue(String key) {
        if (updatedValues.containsKey(key)) {
            return Optional.of(updatedValues.get(key));
        } else {
            return getDefaultValue(key);
        }
    }

    public final boolean hasDefinedValue(String key) {
        return updatedValues.containsKey(key);
    }

    public final boolean hasDefaultValue(String key) {
        return getDefaultValue(key).isPresent();
    }

    protected void checkUpdate(UpdateContext context) {
    }

    void updateConfiguration(Map<String,String> values) {
        if (isSubmitted()) {
            throw new IllegalStateException(this+" is already submitted");
        }
        var ctx = new UpdateContext(values);
        try {
            checkUpdate(ctx);
        } catch (Throwable t) {
            ctx.addValidationError(t.getMessage());
        }
        if (ctx.isValid()) {
            updatedValues.putAll(values);
        } else {
            throw new ConfigurationValidationException(getKind(), getId(), ctx.mainValidationError, ctx.validationErrors);
        }
    }

    public final boolean isComplete() {
        return getKeys().stream().allMatch(key -> !isRequired(key) || hasDefaultValue(key) || hasDefinedValue(key));
    }

    public final boolean isSubmitted() {
        return submitted;
    }

    void setSubmitted(boolean submitted) {
        if (submitted && !isComplete()) {
            throw new IllegalStateException("Cannot submit "+this+" as some required fields are not set");
        }
        this.submitted = submitted;
    }

    protected class UpdateContext {
        private final Map<String, String> updateValues;
        private final Map<String, String> validationErrors = new HashMap<>();
        private String mainValidationError;
        private String validationContext;


        private UpdateContext(Map<String,String> values) {
            this.updateValues = Collections.unmodifiableMap(Objects.requireNonNull(values, "values are required for update"));
        }

        public UpdateContext key(String key) {
            this.validationContext = key;
            return this;
        }

        public UpdateContext setTopLevelContext() {
            this.validationContext = null;
            return this;
        }

        public Set<String> updatedKeys() {
            return updateValues.keySet();
        }

        public boolean isUpdated(String key) {
            return updateValues.containsKey(key);
        }

        public Optional<String> getValue(String key) {
            if (isUpdated(key)) {
                return Optional.of(updateValues.get(key));
            } else {
                return Configuration.this.getValue(key);
            }
        }

        public <T> Optional<T> convert(Function<? super String,T> converter) {
            try {
                return getValue(validationContext).map(converter);
            } catch (RuntimeException re) {
                addValidationError(re.getMessage());
                return Optional.empty();
            }
        }

        public Optional<String> get() {
            return getValue(validationContext);
        }

        public Optional<String> check(Consumer<? super String> check) {
            return convertAndCheck(Function.identity(), check);
        }

        public <T> Optional<T> convertAndCheck(Function<? super String,T> converter, Consumer<? super T> check) {
            try {
                var converted = getValue(validationContext).map(converter);
                converted.ifPresent(check);
                return converted;
            } catch (RuntimeException re) {
                addValidationError(re.getMessage());
                return Optional.empty();
            }
        }


        protected void addValidationError(String message) {
            if (validationContext != null) {
                validationErrors.merge(validationContext, message, (a, b) -> a + "\n" + b);
            } else {
                mainValidationError = mainValidationError == null
                        ? message
                        : mainValidationError + "\n" + message;
            }
        }

        public boolean isValid() {
            return validationErrors.isEmpty() && mainValidationError == null;
        }
    }

}
