/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.deployment;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.common.MavenModelControllerClientConfiguration;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.core.Deployment;
import org.wildfly.plugin.core.DeploymentManager;
import org.wildfly.plugin.core.DeploymentResult;
import org.wildfly.plugin.core.ServerHelper;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The default implementation for executing build plans on the server.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 * @author Stuart Douglas
 */
abstract class AbstractDeployment extends AbstractServerConnection {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    /**
     * The server groups the content should be deployed to.
     */
    @Parameter(alias = "server-groups", property = PropertyNames.SERVER_GROUPS)
    private List<String> serverGroups;

    /**
     * Specifies the name used for the deployment.
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_NAME)
    private String name;

    /**
     * The runtime name for the deployment.
     * <p>
     * In some cases users may wish to have two deployments with the same {@code runtime-name} (e.g. two versions of
     * {@code example.war}) both available in the management configuration, in which case the deployments would need to
     * have distinct {@code name} values but would have the same {@code runtime-name}.
     * </p>
     */
    @Parameter(alias = "runtime-name", property = PropertyNames.DEPLOYMENT_RUNTIME_NAME)
    private String runtimeName;

    @Parameter(defaultValue = "false", property = PropertyNames.DEPLOY_UNMANAGED)
    private boolean unmanaged;

    /**
     * Set to {@code true} if you want the deployment to be skipped, otherwise {@code false}.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    private boolean skip;

    /**
     * The archive file.
     *
     * @return the archive file.
     */
    protected abstract File file();

    /**
     * The goal of the deployment.
     *
     * @return the goal of the deployment.
     */
    public abstract String goal();

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException {
        if (skipExecution()) {
            getLog().debug(String.format("Skipping deployment of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        try (
                ModelControllerClient client = createClient();
                MavenModelControllerClientConfiguration configuration = getClientConfiguration()
        ) {
            final boolean isDomain = ServerHelper.isDomainServer(client);
            validate(isDomain);
            // Deploy the deployment
            getLog().debug("Executing deployment");

            final Deployment deployment = configureDeployment(createDeployment());

            final DeploymentResult result = executeDeployment(DeploymentManager.Factory.create(client), deployment);
            if (!result.successful()) {
                throw new MojoExecutionException(String.format("Failed to execute goal %s: %s", goal(), result.getFailureMessage()));
            }
        } catch (IOException e) {
            throw new MojoFailureException(String.format("Failed to execute goal %s.", goal()), e);
        }
    }

    protected boolean skipExecution() {
        return skip;
    }

    protected abstract DeploymentResult executeDeployment(DeploymentManager deploymentManager, Deployment deployment) throws IOException, MojoDeploymentException;

    protected Deployment createDeployment() {
        return Deployment.of(file());
    }

    /**
     * Validates the deployment.
     *
     * @param isDomain {@code true} if this is a domain server, otherwise {@code false}
     *
     * @throws MojoDeploymentException if the deployment is invalid
     */
    protected void validate(final boolean isDomain) throws MojoDeploymentException {
        final boolean hasServerGroups = hasServerGroups();
        if (isDomain) {
            if (!hasServerGroups) {
                throw new MojoDeploymentException(
                        "Server is running in domain mode, but no server groups have been defined.");
            }
        } else if (hasServerGroups) {
            throw new MojoDeploymentException("Server is running in standalone mode, but server groups have been defined.");
        }
    }

    private Deployment configureDeployment(final Deployment deployment) {
        return deployment.setName(name)
                .setRuntimeName(runtimeName)
                .setUnmanaged(unmanaged)
                .addServerGroups(getServerGroups());
    }

    private Collection<String> getServerGroups() {
        return serverGroups == null ? Collections.emptyList() : serverGroups;
    }

    private boolean hasServerGroups() {
        return serverGroups != null && !serverGroups.isEmpty();
    }
}
