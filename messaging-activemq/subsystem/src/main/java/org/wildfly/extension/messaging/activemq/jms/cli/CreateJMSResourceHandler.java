/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.messaging.activemq.jms.cli;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.handlers.BatchModeCommandHandler;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.operation.impl.DefaultOperationRequestBuilder;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
class CreateJMSResourceHandler extends BatchModeCommandHandler {

    public CreateJMSResourceHandler(CommandContext ctx) {
        super(ctx, "create-jms-resource", true);
        this.addRequiredPath("/subsystem=messaging-activemq");
    }

    @Override
    public ModelNode buildRequestWithoutHeaders(CommandContext ctx) throws OperationFormatException {

        try {
            if(!ctx.getParsedCommandLine().hasProperties()) {
                throw new OperationFormatException("Arguments are missing");
            }
        } catch (CommandFormatException e) {
            throw new OperationFormatException(e.getLocalizedMessage());
        }

        //String target = null;
        String restype = null;
        //String description = null;
        String propsStr = null;
        //boolean enabled = false;
        String jndiName = null;

        String[] args = ctx.getArgumentsString().split("\\s+");
        int i = 0;
        while(i < args.length) {
            String arg = args[i++];
            if(arg.equals("--restype")) {
                if(i < args.length) {
                    restype = args[i++];
                }
            } else if(arg.equals("--target")) {
//                if(i < args.length) {
//                    target = args[i++];
//                }
            } else if(arg.equals("--description")) {
//                if(i < args.length) {
//                    restype = args[i++];
//                }
            } else if(arg.equals("--property")) {
                if (i < args.length) {
                    propsStr = args[i++];
                }
            } else if(arg.equals("--enabled")) {
//                if (i < args.length) {
//                    enabled = Boolean.parseBoolean(args[i++]);
//                }
            } else {
                jndiName = arg;
            }
        }

        if(restype == null) {
            throw new OperationFormatException("Required parameter --restype is missing.");
        }

        if(jndiName == null) {
            throw new OperationFormatException("JNDI name is missing.");
        }

        String name = null;
        String serverName = "default"; // TODO read server name from props
        final Map<String, String> props;
        if(propsStr != null) {
            props = new HashMap<String, String>();
            String[] propsArr = propsStr.split(":");
            for(String prop : propsArr) {
                int equalsIndex = prop.indexOf('=');
                if(equalsIndex < 0 || equalsIndex == prop.length() - 1) {
                    throw new OperationFormatException("Failed to parse property '" + prop + "'");
                }

                String propName = prop.substring(0, equalsIndex).trim();
                String propValue = prop.substring(equalsIndex + 1).trim();
                if(propName.isEmpty()) {
                    throw new OperationFormatException("Failed to parse property '" + prop + "'");
                }

                if(propName.equals("imqDestinationName") ||propName.equalsIgnoreCase("name")) {
                    name = propValue;
                } else if("ClientId".equals(propName)) {
                    props.put("client-id", propValue);
                }
            }
        } else {
            props = Collections.emptyMap();
        }

        if(name == null) {
            name = jndiName.replace('/', '_');
        }

        if(restype.equals("jakarta.jms.Queue")) {

            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.addNode("subsystem", "messaging-activemq");
            builder.addNode("server", serverName);
            builder.addNode("jms-queue", name);
            builder.setOperationName("add");
            builder.getModelNode().get("entries").add(jndiName);

            addProperties(props, builder);

            return builder.buildRequest();

        } else if(restype.equals("jakarta.jms.Topic")) {

            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.addNode("subsystem", "messaging-activemq");
            builder.addNode("server", serverName);
            builder.addNode("jms-topic", name);
            builder.setOperationName("add");
            builder.getModelNode().get("entries").add(jndiName);

            addProperties(props, builder);

            return builder.buildRequest();

        } else if(restype.equals("jakarta.jms.ConnectionFactory") ||
                restype.equals("jakarta.jms.TopicConnectionFactory") ||
                restype.equals("jakarta.jms.QueueConnectionFactory")) {

            DefaultOperationRequestBuilder builder = new DefaultOperationRequestBuilder();
            builder.addNode("subsystem", "messaging-activemq");
            builder.addNode("server", serverName);
            builder.addNode("connection-factory", name);
            builder.setOperationName("add");
            builder.getModelNode().get("entries").add(jndiName);

            addProperties(props, builder);

            return builder.buildRequest();

        } else {
            throw new OperationFormatException("Resource type " + restype + " isn't supported.");
        }
    }

    private void addProperties(Map<String, String> props, DefaultOperationRequestBuilder builder) {
        for (Map.Entry<String, String> entry : props.entrySet()) {
            builder.addProperty(entry.getKey(), entry.getValue());
        }
    }
}
