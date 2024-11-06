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

package be.nabu.eai.module.rest.provider.cache;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

import be.nabu.eai.module.services.iface.ServiceInterfaceManager;
import be.nabu.eai.module.services.vm.VMServiceManager;
import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.services.api.DefinedServiceInterface;
import be.nabu.libs.services.vm.Pipeline;
import be.nabu.libs.services.vm.PipelineInterfaceProperty;
import be.nabu.libs.services.vm.api.VMService;
import be.nabu.libs.services.vm.step.Sequence;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.validator.api.Validation;

public class VMCacheServiceManager extends VMServiceManager {
	
	@Override
	public VMService load(ResourceEntry entry, List<Validation<?>> messages) throws IOException, ParseException {
		Pipeline pipeline = new ServiceInterfaceManager().loadPipeline(entry, messages);

		// next we load the root sequence
		Sequence sequence = parseSequence(new ResourceReadableContainer((ReadableResource) EAIRepositoryUtils.getResource(entry, "service.xml", false)));
		
		final DefinedServiceInterface iface = ValueUtils.getValue(PipelineInterfaceProperty.getInstance(), pipeline.getProperties());
		if (iface == null) {
			throw new ParseException("Could not find the interface for the security service", 0);
		}
		DefinedServiceInterface rewritten = new VMCacheService.CacheInterface(iface);
		pipeline.setProperty(new ValueImpl<DefinedServiceInterface>(PipelineInterfaceProperty.getInstance(), rewritten));
		
		VMCacheService service = new VMCacheService(pipeline);
		service.setRoot(sequence);
		service.setId(entry.getId());
		return service;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Class getArtifactClass() {
		return VMCacheService.class;
	}
}
