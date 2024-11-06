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

package be.nabu.eai.module.rest.provider.iface;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.ResourceWritableContainer;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.definition.xml.XMLDefinitionMarshaller;
import be.nabu.libs.types.structure.Structure;
import be.nabu.libs.validator.api.Validation;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;

public class RESTInterfaceManager extends JAXBArtifactManager<RESTInterfaceConfiguration, RESTInterfaceArtifact> {

	public RESTInterfaceManager() {
		super(RESTInterfaceArtifact.class);
	}

	@Override
	protected RESTInterfaceArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new RESTInterfaceArtifact(id, container, repository);
	}

	@Override
	public RESTInterfaceArtifact load(ResourceEntry entry, List<Validation<?>> messages) throws IOException, ParseException {
		RESTInterfaceArtifact artifact = super.load(entry, messages);
		artifact.setQueryParameters(loadIfExists(entry, "input-query.xml"));
		artifact.setCookie(loadIfExists(entry, "input-cookie.xml"));
		artifact.setSession(loadIfExists(entry, "input-session.xml"));
		artifact.setRequestHeaderParameters(loadIfExists(entry, "input-header.xml"));
		artifact.setPathParameters(loadIfExists(entry, "input-path.xml"));
		artifact.setResponseHeaderParameters(loadIfExists(entry, "input-response-header.xml"));
		return artifact;
	}
	
	private static Structure loadIfExists(ResourceEntry entry, String name) {
		Resource child = entry.getContainer().getChild(name);
		if (child instanceof ReadableResource) {
			try {
				return StructureManager.parse(entry, name);
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

	@Override
	public List<Validation<?>> save(ResourceEntry entry, RESTInterfaceArtifact artifact) throws IOException {
		List<Validation<?>> messages = super.save(entry, artifact);
		format(entry.getContainer(), artifact.getQueryParameters(), "input-query.xml");
		format(entry.getContainer(), artifact.getCookie(), "input-cookie.xml");
		format(entry.getContainer(), artifact.getSession(), "input-session.xml");
		format(entry.getContainer(), artifact.getRequestHeaderParameters(), "input-header.xml");
		format(entry.getContainer(), artifact.getPathParameters(), "input-path.xml");
		format(entry.getContainer(), artifact.getResponseHeaderParameters(), "input-response-header.xml");
		return messages;
	}

	public static List<Validation<?>> format(ResourceContainer<?> container, ComplexType artifact, String name) throws IOException {
		Resource resource = container.getChild(name);
		if (resource == null) {
			resource = ((ManageableContainer<?>) container).create(name, "application/xml");
		}
		WritableContainer<ByteBuffer> writable = new ResourceWritableContainer((WritableResource) resource);
		try {
			XMLDefinitionMarshaller marshaller = new XMLDefinitionMarshaller();
			// we want to ignore the query etc supertypes!
			marshaller.setIgnoreUnknownSuperTypes(true);
			marshaller.marshal(IOUtils.toOutputStream(writable), artifact);
			return new ArrayList<Validation<?>>();
		}
		finally {
			writable.close();
		}
	}
}
