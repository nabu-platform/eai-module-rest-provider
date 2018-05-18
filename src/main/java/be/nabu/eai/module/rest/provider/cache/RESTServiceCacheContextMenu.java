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
