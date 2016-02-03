package be.nabu.eai.module.rest.provider.iface;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class RESTInterfaceManager extends JAXBArtifactManager<RESTInterfaceConfiguration, RESTInterfaceArtifact> {

	public RESTInterfaceManager() {
		super(RESTInterfaceArtifact.class);
	}

	@Override
	protected RESTInterfaceArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new RESTInterfaceArtifact(id, container, repository);
	}

}
