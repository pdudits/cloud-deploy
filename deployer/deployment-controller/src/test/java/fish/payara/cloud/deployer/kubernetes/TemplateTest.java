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

package fish.payara.cloud.deployer.kubernetes;

import org.junit.Test;

import java.util.Scanner;

import static org.junit.Assert.assertEquals;

public class TemplateTest {
    private void assertExpandsTo(String expectation, String template) {
        assertEquals("Testing "+template, expectation, Template.fillTemplate(new Scanner(template), TemplateTest::simpleReplacement));
    }

    static String simpleReplacement(String var) {
        if ("BOOM".equals(var)) {
            throw new IllegalArgumentException("BOOM");
        } else {
            return "["+var+"]";
        }
    }

    @Test
    public void noReplacement() {
        assertExpandsTo("No replacement", "No replacement");
    }

    @Test
    public void singleReplacement() {
        assertExpandsTo("Replacing [VAR] in a string", "Replacing $(VAR) in a string");
    }

    @Test
    public void replacementAtStart() {
        assertExpandsTo("[START] Hello", "$(START) Hello");
    }

    @Test
    public void replacementAtEnd() {
        assertExpandsTo("Hello [END]", "Hello $(END)");
    }

    @Test
    public void incompleteStart() {
        assertExpandsTo("TRICKY) thing", "TRICKY) thing");
    }

    public void incompleteEnd() {
        assertExpandsTo("Tricky ", "Tricky $("); // not good, not terrible
    }

    @Test(expected =  IllegalArgumentException.class)
    public void emptyVar() {
        assertExpandsTo(null, "Wrong $()");
    }

    @Test(expected =  IllegalArgumentException.class)
    public void illegalVar() {
        assertExpandsTo(null, "Wrong $(variable name)");
    }

    @Test
    public void empty() {
        assertExpandsTo("", "");
    }

}
