/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.domain.extension;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelOnlyRemoveStepHandler;
import org.jboss.as.controller.ModelOnlyWriteAttributeHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Fake extension to use in testing extension management.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author Tomaz Cerar (c) 2014 Red Hat Inc.
 * @author Kabir Khan
 */
public class OrderedChildResourceExtension implements Extension {

    public static final String MODULE_NAME = "org.wildfly.extension.ordered-child-resource-test";
    public static final String SUBSYSTEM_NAME = "ordered-children";
    public static final PathElement SUBSYSTEM_PATH = PathElement.pathElement(SUBSYSTEM, SUBSYSTEM_NAME);

    private static final String NAMESPACE = "urn:jboss:test:extension:ordered:child:resource:1.0";
    public static final PathElement CHILD = PathElement.pathElement("child");
    private static final AttributeDefinition ATTR = new SimpleAttributeDefinitionBuilder("attr", ModelType.STRING, true).build();
    private static final AttributeDefinition[] REQUEST_ATTRIBUTES = new AttributeDefinition[]{ATTR};


    @Override
    public void initialize(ExtensionContext context) {
        SubsystemRegistration reg = context.registerSubsystem(SUBSYSTEM_NAME, 1, 1, 1);
        reg.registerXMLElementWriter(SubsystemParser.INSTANCE);
        reg.registerSubsystemModel(new SubsystemResourceDefinition());
    }

    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, NAMESPACE, SubsystemParser.INSTANCE);
    }

    private static class SubsystemResourceDefinition extends SimpleResourceDefinition {

        public SubsystemResourceDefinition() {
            super(SUBSYSTEM_PATH,
                    new NonResolvingResourceDescriptionResolver(),
                    new AbstractAddStepHandler(REQUEST_ATTRIBUTES) {
                        @Override
                        protected ResourceCreator getResourceCreator() {
                            return new OrderedResourceCreator(false, CHILD.getKey());
                        }

                    },
                    new ModelOnlyRemoveStepHandler());
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerReadWriteAttribute(ATTR, null, new ModelOnlyWriteAttributeHandler(REQUEST_ATTRIBUTES));
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            super.registerOperations(resourceRegistration);
            resourceRegistration.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            resourceRegistration.registerSubModel(new OrderedChildResourceDefinition());
        }
    }

    private static class OrderedChildResourceDefinition extends SimpleResourceDefinition {

        public OrderedChildResourceDefinition() {
            super(CHILD, new NonResolvingResourceDescriptionResolver(),
                    new AbstractAddStepHandler() {
                        @Override
                        protected ResourceCreator getResourceCreator() {
                            return new OrderedResourceCreator(true);
                        }

                    },
                    new ModelOnlyRemoveStepHandler());
        }

        @Override
        protected boolean isOrderedChildResource() {
            return true;
        }
    }

    private static class SubsystemParser implements XMLStreamConstants, XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {
        static final SubsystemParser INSTANCE = new SubsystemParser();
        @Override
        public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context)
                throws XMLStreamException {
            context.startSubsystemElement(OrderedChildResourceExtension.NAMESPACE, false);
            final ModelNode node = context.getModelNode();
            if (node.hasDefined("child")) {
                for (Property prop : node.get("child").asPropertyList()) {
                    writer.writeStartElement("child");
                    writer.writeAttribute("name", prop.getName());
                    writer.writeEndElement();
                }
            }
            writer.writeEndElement();
        }

        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            ParseUtils.requireNoAttributes(reader);
            list.add(Util.createAddOperation(PathAddress.pathAddress(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME)));

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                final String element = reader.getLocalName();
                switch (element) {
                    case "child": {
                        list.add(parseChild(reader));
                        break;
                    }
                    default: {
                        throw ParseUtils.unexpectedElement(reader);
                    }
                }
            }
        }

        private ModelNode parseChild(XMLExtendedStreamReader reader) throws XMLStreamException {
            ModelNode add = null;
            int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {
                final String value = reader.getAttributeValue(i);
                final String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case "name": {
                        add = Util.createAddOperation(PathAddress.pathAddress(
                                PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, SUBSYSTEM_NAME),
                                PathElement.pathElement("child", value)));
                        break;
                    }
                    default: {
                        throw ParseUtils.unexpectedAttribute(reader, i);
                    }
                }
            }
            if (add == null) {
                throw ParseUtils.missingRequired(reader, Collections.singleton("child"));
            }
            // Require no content
            ParseUtils.requireNoContent(reader);
            return add;
        }
    }


}
