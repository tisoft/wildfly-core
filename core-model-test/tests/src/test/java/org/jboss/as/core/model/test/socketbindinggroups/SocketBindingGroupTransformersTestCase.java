/*
* JBoss, Home of Professional Open Source.
* Copyright 2012, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.core.model.test.socketbindinggroups;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.AGENT_LIB;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.AGENT_PATH;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.ENVIRONMENT_VARIABLES;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.ENV_CLASSPATH_IGNORED;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.HEAP_SIZE;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.JAVA_AGENT;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.JAVA_HOME;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.LAUNCH_COMMAND;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.MAX_HEAP_SIZE;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.MAX_PERMGEN_SIZE;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.OPTIONS;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.PERMGEN_SIZE;
import static org.jboss.as.host.controller.model.jvm.JvmAttributes.STACK_SIZE;

import java.util.List;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.CoreModelTestDelegate;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.KernelServicesBuilder;
import org.jboss.as.core.model.test.LegacyKernelServicesInitializer;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.core.model.test.TransformersTestParameterized;
import org.jboss.as.core.model.test.TransformersTestParameterized.TransformersParameter;
import org.jboss.as.core.model.test.util.ExcludeCommonOperations;
import org.jboss.as.core.model.test.util.TransformersTestParameter;
import org.jboss.as.model.test.FailedOperationTransformationConfig;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.network.SocketBinding;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
@RunWith(TransformersTestParameterized.class)
public class SocketBindingGroupTransformersTestCase extends AbstractCoreModelTest {

    private final ModelVersion modelVersion;
    private final ModelTestControllerVersion testControllerVersion;

    @TransformersParameter
    public static List<TransformersTestParameter> parameters(){
        return TransformersTestParameter.setupVersions();
    }

    public SocketBindingGroupTransformersTestCase(TransformersTestParameter params) {
        this.modelVersion = params.getModelVersion();
        this.testControllerVersion = params.getTestControllerVersion();
    }

    @Test
    public void testSocketBindingGroupsTransformer() throws Exception {
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXmlResource("domain-transform.xml");

        builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion);

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());

        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        checkCoreModelTransformation(mainServices, modelVersion);
    }

    @Test
    public void testRejectIncludes() throws Exception {
        if (modelVersion.getMajor() >= 4) {
            return;
        }
        KernelServicesBuilder builder = createKernelServicesBuilder(TestModelType.DOMAIN);
        builder.createLegacyKernelServicesBuilder(modelVersion, testControllerVersion);

        KernelServices mainServices = builder.build();
        Assert.assertTrue(mainServices.isSuccessfulBoot());

        KernelServices legacyServices = mainServices.getLegacyServices(modelVersion);
        Assert.assertTrue(legacyServices.isSuccessfulBoot());

        //Get the boot operations from the xml file
        List<ModelNode> operations = builder.parseXmlResource("domain.xml");

        //Run the standard tests trying to execute the parsed operations.
        PathAddress addr = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "test-with-includes");
        FailedOperationTransformationConfig config = new FailedOperationTransformationConfig();
        config.addFailedAttribute(addr, new FailedOperationTransformationConfig.NewAttributesConfig(INCLUDES));
        ModelTestUtils.checkFailedTransformedBootOperations(mainServices, modelVersion, operations, config);
    }
}
