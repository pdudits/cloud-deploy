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
 * A configuration is identified within deployment process by its {@link #getKind() kind} and {@link #getId() id}.
 * Configuration can be mutated over its owning {@link DeploymentProcessState}.
 * <p>
 *     There are two significant state of a configuration. A configuration {@linkplain #isComplete() is complete} when all
 * {@linkplain #isRequired(String) required} fields are set. When configuration is complete it can be
 * {@linkplain #setSubmitted(boolean) submitted} and marked final with that.
 * </p>
 */
public abstract class Configuration {
    private final String id;
    private boolean submitted;
    private final Map<String,String> updatedValues = new HashMap<>();

    /**
     * Create configuration with given id.
     * @param id id of the configuration
     */
    protected Configuration(String id) {
        if (id.contains(":")) {
            throw new IllegalArgumentException("Invalid configuration id "+id+" name cannot contain colon");
        }
        this.id = id;
    }

    /**
     * Kind of configuration. Kind usually represents semantic of the individual configuration keys.
     * @return representation of kind
     */
    public abstract String getKind();

    /**
     * Return id.
     * @return id
     */
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

    /**
     * Return set of keys in this configuration
     * @return unmodifiable collection of keys
     */
    public abstract Set<String> getKeys();

    /**
     * Determine if a key is required in this configuration.
     * Required keys are verified to be set before submission.
     * @param key
     * @return
     */
    public boolean isRequired(String key) {
        return false;
    }

    /**
     * Get default value for a key.
     * @param key key to get value for
     * @return non-empty optional, if default value for key exists, empty Optional otherwise.
     */
    public Optional<String> getDefaultValue(String key) {
        return Optional.empty();
    }

    /**
     * Get currently set value for a key. If the key was not set by update operation, default value is returned.
     * @param key key to get value for
     * @return non-empty optional, if value is defined, empty Optional otherwise.
     */
    public final Optional<String> getValue(String key) {
        if (updatedValues.containsKey(key)) {
            return Optional.of(updatedValues.get(key));
        } else {
            return getDefaultValue(key);
        }
    }

    /**
     * Check if value was set for key
     * @param key key to check
     * @return true if value was provided by means of update operation
     */
    public final boolean hasDefinedValue(String key) {
        return updatedValues.containsKey(key);
    }

    /**
     * Check if value has default value
     * @param key key to check
     * @return true if {@link #getDefaultValue(String)} returns non-empty optional for key
     */
    public final boolean hasDefaultValue(String key) {
        return getDefaultValue(key).isPresent();
    }

    /**
     * Validate update operation.
     * Implementations can utilize {@link UpdateContext} to check values changed in update, and to flag validation
     * errors in that context.
     * During check operation, {@link #getValue(String)} method will return previous value for the key. To obtain
     * updated value, use {@link UpdateContext#getValue(String)}.
     * Runtime exceptions thrown from this method are added as validation errors in context active at time of throw.
     * @param context the context of the update
     * @see UpdateContext#convertAndCheck(Function, Consumer)
     * @see UpdateContext#key(String)
     * @see UpdateContext#addValidationError(String)
     */
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

    /**
     * Check if configuration is complete.
     * Configuration is complete when all required fields have values.
     * @return
     */
    public final boolean isComplete() {
        return getKeys().stream().allMatch(key -> !isRequired(key) || hasDefaultValue(key) || hasDefinedValue(key));
    }


    /**
     * Check if configuration is submitted.
     * @return true if submitted
     */
    public final boolean isSubmitted() {
        return submitted;
    }

    /**
     * Set submission flag
     * @param submitted flag to set
     * @throws IllegalStateException when configuration is not complete
     */
    void setSubmitted(boolean submitted) {
        if (submitted && !isComplete()) {
            throw new IllegalStateException("Cannot submit "+this+" as some required fields are not set");
        }
        this.submitted = submitted;
    }

    void reset() {
        setSubmitted(false);
        updatedValues.clear();
    }

    /**
     * The context of the update. Method {@link #checkUpdate(UpdateContext)} utilize this class to validate fields
     * and report validation errors.
     * Methods of this class operate in context of specific key, chosen by {@link #key(String)}. Conversions and checks
     * are performed in context of this key. When an exception is thrown the current context is reported in the exception.
     * Choosing top-level context with {@link #topLevel()} causes thrown exception not to refer to any specific key.
     */
    protected class UpdateContext {
        private final Map<String, String> updateValues;
        private final Map<String, String> validationErrors = new HashMap<>();
        private String mainValidationError;
        private String validationContext;


        private UpdateContext(Map<String,String> values) {
            this.updateValues = Collections.unmodifiableMap(Objects.requireNonNull(values, "values are required for update"));
        }

        /**
         * Set the context to specified key
         * @param key key to set
         * @return this context
         */
        public UpdateContext key(String key) {
            this.validationContext = key;
            return this;
        }

        /**
         * Set context to top-level one.
         * @return this context
         */
        public UpdateContext topLevel() {
            this.validationContext = null;
            return this;
        }

        /**
         * List of keys updated in this operation
         * @return unmodifiable set of keys
         */
        public Set<String> updatedKeys() {
            return updateValues.keySet();
        }

        /**
         * Check if value is updated in this operation
         * @param key key to check
         * @return true if value is updated in this operation
         */
        public boolean isUpdated(String key) {
            return updateValues.containsKey(key);
        }

        /**
         * Get effective value of key after this update operation
         * @param key key to check
         * @return value provided in update, or value currently present in configuration if the key wasn't updated
         */
        public Optional<String> getValue(String key) {
            if (isUpdated(key)) {
                return Optional.of(updateValues.get(key));
            } else {
                return Configuration.this.getValue(key);
            }
        }

        /**
         * Return converted value of context key. Any exception thrown from converter is captured and stored as violation
         * in context of current key. Exception is not rethrown, rather empty optional is returned.
         * @param converter converter to invoke
         * @param <T> target type of t
         * @return Non-empty optional if value is defined and conversion succeeds. Empty optional otherwise
         */
        public <T> Optional<T> convert(Function<? super String,T> converter) {
            try {
                return getValue(validationContext).map(converter);
            } catch (Throwable t) {
                addValidationError(t.getMessage());
                return Optional.empty();
            }
        }

        /**
         * Return current value of context key
         * @return Non-empty optional if value is defined.
         */
        public Optional<String> get() {
            return getValue(validationContext);
        }

        /**
         * Check a value. Any exceptions thrown from check are captured and stored as violations in context of current key.
         * @param check
         * @return
         */
        public Optional<String> check(Consumer<? super String> check) {
            return convertAndCheck(Function.identity(), check);
        }

        /**
         * Convert value, and check it. Any exception thrown from converter or check is captured and stored as violation
         * in context of current key. Exception is not rethrown, rather empty optional is returned.
         * @param converter
         * @param check
         * @param <T>
         * @return
         */
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

        /**
         * Add validation related to current context. Can be used to explicitly add validation error without throwing
         * an exception.
         * @param message
         */
        public void addValidationError(String message) {
            if (validationContext != null) {
                validationErrors.merge(validationContext, message, (a, b) -> a + "\n" + b);
            } else {
                mainValidationError = mainValidationError == null
                        ? message
                        : mainValidationError + "\n" + message;
            }
        }

        /**
         * Check if update operation is valid
         * @return true when no validation errors have been reported
         */
        public boolean isValid() {
            return validationErrors.isEmpty() && mainValidationError == null;
        }
    }

}
