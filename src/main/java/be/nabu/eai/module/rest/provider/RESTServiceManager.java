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
