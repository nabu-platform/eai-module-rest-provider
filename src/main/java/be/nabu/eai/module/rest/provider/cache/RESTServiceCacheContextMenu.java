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

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.module.rest.provider.RESTService;
import be.nabu.eai.module.rest.provider.RESTServiceManager;
import be.nabu.eai.module.rest.provider.iface.RESTInterfaceArtifact;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.libs.services.api.DefinedService;

public class RESTServiceCacheContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		if (entry.isNode() && RESTService.class.isAssignableFrom(entry.getNode().getArtifactClass()) && entry.isEditable()) {
			try {
				final RESTService service = (RESTService) entry.getNode().getArtifact();
				if (service.getArtifact("cache") == null) {
					MenuItem item = new MenuItem("Add Cache");
					item.setGraphic(MainController.loadGraphic("add.png"));
					item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							service.addArtifact("cache", new VMCacheService((DefinedService) service.getArtifact("implementation")), service.getConfiguration(service.getArtifact("implementation")));
							RESTInterfaceArtifact api = service.getArtifact("api");
							api.getConfig().setCache(true);
							try {
								new RESTServiceManager().save((ResourceEntry) entry, service);
							}
							catch (IOException e) {
								throw new RuntimeException(e);
							}
							// refresh visually
							MainController.getInstance().refresh(entry.getId());
							// refresh server
							MainController.getInstance().getAsynchronousRemoteServer().reload(entry.getId());
							MainController.getInstance().getCollaborationClient().updated(entry.getId(), "Added cache implementation");
						}
					});
					return item;
				}
				else {
					MenuItem item = new MenuItem("Remove Cache");
					item.setGraphic(MainController.loadGraphic("remove.png"));
					item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							service.removeArtifact("cache");
							RESTInterfaceArtifact api = service.getArtifact("api");
							api.getConfig().setCache(false);
							try {
								new RESTServiceManager().save((ResourceEntry) entry, service);
							}
							catch (IOException e) {
								throw new RuntimeException(e);
							}
							MainController.getInstance().refresh(entry.getId());
							MainController.getInstance().getAsynchronousRemoteServer().reload(entry.getId());
							MainController.getInstance().getCollaborationClient().updated(entry.getId(), "Removed cache implementation");
						}
					});
					return item;
				}
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return null;
	}

}
