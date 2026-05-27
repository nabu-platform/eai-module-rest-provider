/*
* Copyright (C) 2016 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.rest.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.eai.module.rest.provider.iface.RESTInterfaceArtifact;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.artifacts.container.ContainerArtifactManager;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.PipelineInterfaceProperty;
import be.nabu.libs.services.vm.SimpleVMServiceDefinition;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.structure.Structure;

public class RESTServiceManager extends ContainerArtifactManager<RESTService> {
	
	public static final String ACTUAL_ID = "actualId";
	public static final String INTERFACE_EDITABLE = "interfaceEditable";

	public static RESTService createDefaultService(String id, ResourceContainer<?> container, Repository repository) {
		RESTService restService = new RESTService(id);
		Map<String, String> apiConfiguration = new HashMap<String, String>();
		apiConfiguration.put(ACTUAL_ID, id);
		RESTInterfaceArtifact api = new RESTInterfaceArtifact("$self:api", container, repository);
		api.getConfig().setRoles(new ArrayList<String>(Arrays.asList("$user")));
		restService.addArtifact("api", api, apiConfiguration);
		Pipeline pipeline = new Pipeline(new Structure(), new Structure());
		pipeline.setProperty(new ValueImpl<DefinedServiceInterface>(PipelineInterfaceProperty.getInstance(), api));
		SimpleVMServiceDefinition implementation = new SimpleVMServiceDefinition(pipeline);
		implementation.setId("$self:implementation");
		Map<String, String> implementationConfiguration = new HashMap<String, String>(apiConfiguration);
		implementationConfiguration.put(INTERFACE_EDITABLE, "false");
		restService.addArtifact("implementation", implementation, implementationConfiguration);
//		VMAuthorizationService authorization = new VMAuthorizationService(implementation);
//		authorization.setId(id + ":security");
//		restService.addArtifact("security", authorization, implementationConfiguration);
		return restService;
	}
	
	@Override
	public RESTService newInstance(String id) {
		return new RESTService(id);
	}

	@Override
	public Class<RESTService> getArtifactClass() {
		return RESTService.class;
	}

	@Override
	protected List<ResourceContainer<?>> getChildrenToLoad(ResourceContainer<?> directory) {
		List<ResourceContainer<?>> children = new ArrayList<ResourceContainer<?>>();
		children.add((ResourceContainer<?>) directory.getChild("api"));
		children.add((ResourceContainer<?>) directory.getChild("implementation"));
		children.add((ResourceContainer<?>) directory.getChild("security"));
		children.add((ResourceContainer<?>) directory.getChild("cache"));
		return children;
	}

}
