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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigurationTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private DeploymentProcessState processState = new DeploymentProcessState(null, null);;
    private MockConfiguration conf = new MockConfiguration("id");
    private SimpleMockConfiguration simple = new SimpleMockConfiguration("id");


    @Test
    public void addingConfigurationBroadcastsEvent() {
        assertEquals(ChangeKind.CONFIGURATION_ADDED, processState.addConfiguration(conf));
        assertEquals(ChangeKind.CONFIGURATION_ADDED, processState.addConfiguration(new MockConfiguration("other-mock")));
        assertEquals(ChangeKind.CONFIGURATION_ADDED, processState.addConfiguration(simple));
    }

    @Test(expected = IllegalArgumentException.class)
    public void addingDuplicateConfigurationFails() {
        assertEquals(ChangeKind.CONFIGURATION_ADDED, processState.addConfiguration(conf));
        assertEquals(ChangeKind.CONFIGURATION_ADDED, processState.addConfiguration(conf));
    }

    @Test
    public void configurationIsNotSubmittedByDefault() {
        assertFalse("Newly created configuration should be unsubmitted", conf.isSubmitted());
    }

    @Test
    public void defaultValueIsUsedWhenNotOverriden() {
        assertEquals("DefaultValue", conf.getValue("defaulted").get());
        var update = Map.of("required","val", "optional", "opt");
        conf.updateConfiguration(update);
        assertEquals("DefaultValue", conf.getValue("defaulted").get());
    }

    @Test
    public void defaultValueOverridable() {
        assertEquals("DefaultValue", conf.getValue("defaulted").get());
        var update = Map.of("required","val", "optional", "opt", "defaulted", "val");
        conf.updateConfiguration(update);
        assertEquals("val", conf.getValue("defaulted").get());
    }

    @Test
    public void nonOverridenFieldsNotPresent() {
        assertEquals(Optional.empty(), conf.getValue("required"));
        assertEquals(Optional.empty(), conf.getValue("optional"));
    }

    @Test
    public void configWithRequiredFieldsOverridenIsComplete() {
        conf.updateConfiguration(Map.of("required", "value"));
        assertTrue("Configuration with required fields filled should be complete", conf.isComplete());
    }

    @Test
    public void configWithNoRequiredFieldsIsComplete() {
        assertTrue("Configuration with no required fields should be complete", simple.isComplete());
    }

    @Test
    public void completeConfigCanBeSubmitted() {
        assertTrue("Configuration with no required fields should be complete", simple.isComplete());
        simple.setSubmitted(true);
        assertTrue(simple.isSubmitted());
    }

    @Test(expected = IllegalStateException.class)
    public void submittedConfigCannotBeChanged() {
        simple.setSubmitted(true);
        assertTrue(simple.isSubmitted());
        simple.updateConfiguration(Map.of("a","a"));
    }

    @Test(expected = IllegalStateException.class)
    public void incompleteConfigCannotBeSubmitted() {
        conf.setSubmitted(true);
    }

    @Test
    public void completeConfigCanBeUnsubmitted() {
        assertTrue("Configuration with no required fields should be complete", simple.isComplete());
        simple.setSubmitted(true);
        // once more
        simple.setSubmitted(true);
        simple.setSubmitted(false);
        assertFalse(simple.isSubmitted());
    }

    @Test
    public void allConfigsCanBeSubmitted() {
        var otherMock = new MockConfiguration("other-mock");
        assertEquals(ChangeKind.CONFIGURATION_ADDED, processState.addConfiguration(conf));
        assertEquals(ChangeKind.CONFIGURATION_ADDED, processState.addConfiguration(otherMock));
        var update = Map.of("required", "value");
        processState.setConfiguration("mock", "id", false, update);
        processState.setConfiguration("mock", "other-mock", false, update);
        processState.getConfigurations().stream().forEach(c -> {
            assertTrue("Configuration should be complete", c.isComplete());
            assertFalse("Configuration should not be submitted", c.isSubmitted());
        });
        processState.submitConfigurations(true);
        assertTrue(conf.isSubmitted());
        assertTrue(otherMock.isSubmitted());
    }

    @Test
    public void configsAreSubmittedAtomically() {
        var otherMock = new MockConfiguration("other-mock");
        assertEquals(ChangeKind.CONFIGURATION_ADDED, processState.addConfiguration(conf));
        assertEquals(ChangeKind.CONFIGURATION_ADDED, processState.addConfiguration(otherMock));
        var update = Map.of("required", "value");
        processState.setConfiguration("mock", "id", false, update);
        try {
            processState.submitConfigurations(true);
            fail("Submitting with incomplete configurations should have failed");
        } catch (IllegalStateException ise) {
            // expected
        }
        processState.getConfigurations().stream().forEach(c -> {
            assertFalse("None configuration should be submitted", c.isSubmitted());
        });
    }

    @Test
    public void validationErrorRefersToField() {
        // not the best message out there, but specific enough.
        thrown.expectMessage("number: For input string: \"three\"");
        conf.updateConfiguration(Map.of("number", "three"));
    }

    @Test
    public void multipleValidationErrorsAreReported() {
        thrown.expectMessage("number: For input string: \"three\"");
        thrown.expectMessage("optional: Should start with 'o'");
        conf.updateConfiguration(Map.of("number", "three", "optional", "three"));
    }

    @Test
    public void conversionErrorBeforeValidationLogic() {
        thrown.expectMessage("biggerNumber: For input string: \"three\"");
        conf.updateConfiguration(Map.of("number", "1", "biggerNumber", "three"));
    }

    @Test
    public void validationLogicErrorReferToField() {
        thrown.expectMessage("biggerNumber: number is required when bigger is present");
        conf.updateConfiguration(Map.of("biggerNumber", "2"));
    }

    @Test
    public void validationErrorsRejectAtomically() {
        conf.updateConfiguration(Map.of("required", "req"));
        try {
            conf.updateConfiguration(Map.of("biggerNumber", "2", "required", "5"));
        } catch (IllegalArgumentException e) {
            //expected
        }
        assertEquals("Whole update should be rejected on validation error", "req", conf.getValue("required").get());
    }

    @Test
    public void topLevelValidationFailureReflectedInException() {
        thrown.expectMessage("Even number of fields should be updated at time");
        conf.updateConfiguration(Map.of("optional","odd"));
    }

    @Test
    public void topLevelValidationFailureAndFieldsReflectedInException() {
        thrown.expectMessage("Even number of fields should be updated at time");
        thrown.expectMessage("biggerNumber: should be bigger than number");
        conf.updateConfiguration(Map.of("optional","odd","number", "3", "biggerNumber","1"));
    }

    static class MockConfiguration extends Configuration {
        static final Set<String> KEYS = Set.of("required", "defaulted", "optional", "number", "biggerNumber");

        public MockConfiguration(String id) {
            super(id);
        }

        @Override
        public String getKind() {
            return "mock";
        }

        @Override
        public Set<String> getKeys() {
            return KEYS;
        }

        @Override
        public Optional<String> getDefaultValue(String key) {
            switch(key) {
                case "defaulted":
                    return Optional.of("DefaultValue");
                default:
                    return Optional.empty();
            }
        }

        @Override
        public boolean isRequired(String key) {
            return "required".equals(key);
        }

        @Override
        protected void checkUpdate(UpdateContext context) {
            var number = context.key("number").convert(Integer::parseInt);
            var biggerNumber = context.key("biggerNumber")
                    .convertAndCheck(Integer::parseInt, bigger -> checkBigger(bigger, number));
            var optional = context.key("optional").check(this::startsWithO);

            if (optional.isPresent() && optional.get().equals("odd") && context.updatedKeys().size() % 2 == 1) {
                context.setTopLevelContext();
                // That's odd validation, isn't it?
                // In reality, validation that requires multiple fields to be changed.
                throw new IllegalArgumentException("Even number of fields should be updated at time");
            }
        }

        private void startsWithO(String s) {
            if (!s.startsWith("o")) {
                throw new IllegalArgumentException("Should start with 'o'");
            }
        }

        private void checkBigger(Integer bigger, Optional<Integer> number) {
            if (number.isPresent()) {
                if (bigger <= number.get()) {
                    throw new IllegalArgumentException("should be bigger than number");
                }
            } else {
                throw new IllegalArgumentException("number is required when bigger is present");
            }
        }
    }

    static class SimpleMockConfiguration extends Configuration {
        public SimpleMockConfiguration(String id) {
            super(id);
        }

        @Override
        public String getKind() {
            return "SimpleMock";
        }

        @Override
        public Set<String> getKeys() {
            return Set.of("a", "b", "c");
        }
    }
}
