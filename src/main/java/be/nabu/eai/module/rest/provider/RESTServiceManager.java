package be.nabu.eai.module.rest.provider;

import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.repository.artifacts.container.ContainerArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class RESTServiceManager extends ContainerArtifactManager<RESTService> {
	
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
