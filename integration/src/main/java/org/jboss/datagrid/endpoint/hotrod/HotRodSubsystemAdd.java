/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.datagrid.endpoint.hotrod;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

import javax.management.MBeanServer;

import org.infinispan.server.hotrod.HotRodServer;
import org.jboss.as.controller.BasicOperationResult;
import org.jboss.as.controller.ModelAddOperationHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationResult;
import org.jboss.as.controller.ResultHandler;
import org.jboss.as.controller.RuntimeTask;
import org.jboss.as.controller.RuntimeTaskContext;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.services.path.RelativePathService;
import org.jboss.datagrid.endpoint.EndpointAttributes;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceBuilder.DependencyType;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * @author Emanuel Muckenhuber
 */
class HotRodSubsystemAdd implements ModelAddOperationHandler {

    private static final String DEFAULT_PATH = "configuration/datagrid-endpoint-hotrod.properties";
    private static final String DEFAULT_RELATIVE_TO = "jboss.server.base.dir";
    private static final ServiceName PATH_BASE = HotRodServiceNames.HOTROD.append("paths");

    static final HotRodSubsystemAdd INSTANCE = new HotRodSubsystemAdd();

    /** {@inheritDoc} */
    @Override
    public OperationResult execute(final OperationContext context, final ModelNode operation, final ResultHandler resultHandler) {

        final ModelNode compensatingOperation = Util.getResourceRemoveOperation(operation.require(OP_ADDR));

        // Populate subModel
        final ModelNode subModel = context.getSubModel();
        subModel.setEmptyObject();
        if (operation.hasDefined(EndpointAttributes.CONFIG_PATH)) {
            subModel.get(EndpointAttributes.CONFIG_PATH).set(operation.get(EndpointAttributes.CONFIG_PATH));
        }

        if (context.getRuntimeContext() != null) {
            context.getRuntimeContext().setRuntimeTask(new RuntimeTask() {
                @Override
                public void execute(RuntimeTaskContext context) throws OperationFailedException {
                    final ServiceTarget serviceTarget = context.getServiceTarget();
                    // Create the service
                    final HotRodService service = new HotRodService();

                    // Add the service
                    final ServiceBuilder<HotRodServer> serviceBuilder = serviceTarget.addService(HotRodServiceNames.HOTROD, service)
                            .addDependency(DependencyType.OPTIONAL, ServiceName.JBOSS.append("mbean", "server"), MBeanServer.class, service.getMBeanServer());

                    // Create path services
                    serviceBuilder.addDependency(createConfigPathService(operation, serviceTarget),
                            String.class, service.getConfigPathInjector());

                    // Install the service
                    serviceBuilder.install();

                    resultHandler.handleResultComplete();
                }
            });
        } else {
            resultHandler.handleResultComplete();
        }
        return new BasicOperationResult(compensatingOperation);
    }

    static ServiceName createConfigPathService(final ModelNode operation, final ServiceTarget serviceTarget) {
        ModelNode path = operation.get(EndpointAttributes.CONFIG_PATH);
        final ServiceName serviceName = PATH_BASE.append(EndpointAttributes.CONFIG_PATH);
        final String relativeTo = path.hasDefined(RELATIVE_TO) ? path.get(RELATIVE_TO).asString() : DEFAULT_RELATIVE_TO;
        final String pathName = path.hasDefined(PATH) ? path.get(PATH).asString() : DEFAULT_PATH;
        RelativePathService.addService(serviceName, pathName, relativeTo, serviceTarget);
        return serviceName;
    }
}