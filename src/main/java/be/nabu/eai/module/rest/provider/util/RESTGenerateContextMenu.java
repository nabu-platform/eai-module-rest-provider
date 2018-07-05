package be.nabu.eai.module.rest.provider.util;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.module.rest.WebMethod;
import be.nabu.eai.module.rest.provider.RESTService;
import be.nabu.eai.module.rest.provider.RESTServiceGUIManager;
import be.nabu.eai.module.rest.provider.RESTServiceManager;
import be.nabu.eai.module.rest.provider.iface.RESTInterfaceArtifact;
import be.nabu.eai.module.types.structure.StructureManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.jfx.control.tree.TreeItem;
import be.nabu.libs.property.ValueUtils;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;
import be.nabu.libs.types.base.ComplexElementImpl;
import be.nabu.libs.types.base.SimpleElementImpl;
import be.nabu.libs.types.base.TypeBaseUtils;
import be.nabu.libs.types.base.ValueImpl;
import be.nabu.libs.types.java.BeanResolver;
import be.nabu.libs.types.properties.CollectionNameProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.PrimaryKeyProperty;
import be.nabu.libs.types.structure.DefinedStructure;
import be.nabu.libs.types.structure.Structure;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.WritableContainer;

public class RESTGenerateContextMenu implements EntryContextMenuProvider {

	@Override
	public MenuItem getContext(Entry entry) {
		if (!entry.isLeaf() && !entry.isNode()) {
			MenuItem item = new MenuItem("Generate REST CRUD Services");
			item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@Override
				public void handle(ActionEvent arg0) {
					Set<Property<?>> properties = new LinkedHashSet<Property<?>>();
					properties.add(new SimpleProperty<DefinedType>("Type", DefinedType.class, true));
					properties.add(new SimpleProperty<Boolean>("Overwrite", Boolean.class, false));
					final SimplePropertyUpdater updater = new SimplePropertyUpdater(true, properties);
					EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Generate structure from JSON", new EventHandler<ActionEvent>() {
						@SuppressWarnings({ "unchecked", "rawtypes" })
						@Override
						public void handle(ActionEvent arg0) {
							DefinedType type = updater.getValue("Type");
							Boolean overwrite = updater.getValue("Overwrite");
							if (overwrite == null) {
								overwrite = false;
							}
							if (type instanceof ComplexType) {
//								StructureManager;
								
								
								try {
									// create the structures
									String collectionName = ValueUtils.getValue(CollectionNameProperty.getInstance(), type.getProperties());
									String name = type.getName();

									// we make a structure that will serve as the input of the create/update services
									DefinedStructure inputStructure = new DefinedStructure();
									name = name.substring(0, 1).toLowerCase() + name.substring(1);
									
									if (collectionName == null) {
										collectionName = name.endsWith("s") ? name : name + "s";
									}
									else {
										collectionName = collectionName.substring(0, 1).toLowerCase() + collectionName.substring(1);	
									}
									inputStructure.setId(entry.getId() + "." + name + "Input");
									inputStructure.setName(name);
									Element<?> primaryKeyField = null;
									for (Element<?> child : TypeUtils.getAllChildren((ComplexType) type)) {
										Value<Boolean> primaryKey = child.getProperty(PrimaryKeyProperty.getInstance());
										if (primaryKey != null && primaryKey.getValue()) {
											primaryKeyField = child;
										}
										// we skip created and modified as we generally use them as timestamps for creation & modification of the record
										// they are automanaged
										else if (!child.getName().equals("created") && !child.getName().equals("modified")) {
											inputStructure.add(TypeBaseUtils.clone(child, inputStructure));
										}
									}
									
									DefinedStructure outputStructure = new DefinedStructure();
									outputStructure.setId(entry.getId() + "." + name);
									outputStructure.setSuperType(inputStructure);
									outputStructure.setName(name);
									// add the primary key field
									if (primaryKeyField != null) {
										outputStructure.add(TypeBaseUtils.clone(primaryKeyField, outputStructure));
									}
									
									DefinedStructure listStructure = new DefinedStructure();
									listStructure.setId(entry.getId() + "." + name + "List");
									listStructure.setName(name + "List");
									listStructure.add(new ComplexElementImpl(collectionName, outputStructure, listStructure, 
										new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0),
										new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)
									));
									listStructure.add(new ComplexElementImpl("page", (ComplexType) BeanResolver.getInstance().resolve("nabu.services.jdbc.types.Page"),listStructure, new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0)));
									
									// write the structures
									StructureManager structureManager = new StructureManager();
									
									// only save the structures if they don't exist yet, we don't want to throw away customizations
									RepositoryEntry inputEntry = (RepositoryEntry) entry.getChild(name + "Input");
									if (inputEntry == null || overwrite) {
										inputEntry = ((RepositoryEntry) entry).createNode(name + "Input", structureManager, true);
										structureManager.save(inputEntry, inputStructure);
									}
									RepositoryEntry outputEntry = (RepositoryEntry) entry.getChild(name);
									if (outputEntry == null || overwrite) {
										outputEntry = ((RepositoryEntry) entry).createNode(name, structureManager, true);
										structureManager.save(outputEntry, outputStructure);
									}
									RepositoryEntry listEntry = (RepositoryEntry) entry.getChild(name + "List");
									if (listEntry == null || overwrite) {
										listEntry = ((RepositoryEntry) entry).createNode(name + "List", structureManager, true);
										structureManager.save(listEntry, listStructure);
									}
									
									// create the rest services
									RESTServiceGUIManager guiManager = new RESTServiceGUIManager();
									RESTServiceManager restManager = new RESTServiceManager();

									// create REST service
									RepositoryEntry createEntry = (RepositoryEntry) entry.getChild("create");
									if (createEntry == null) {
										createEntry = ((RepositoryEntry) entry).createNode("create", restManager, true);
										RESTService create = guiManager.newInstance(MainController.getInstance(), createEntry);
										RESTInterfaceArtifact iface = create.getArtifact("api");
										iface.getConfig().setMethod(WebMethod.POST);
										iface.getConfig().setPath("/" + name);
										iface.getConfig().setInput(inputStructure);
										iface.getConfig().setOutput(outputStructure);
										iface.getConfig().setRoles(Arrays.asList("$user"));
										iface.getConfig().setPermissionAction(name + ".create");
										restManager.save(createEntry, create);
										writeResource(
											(WritableResource) ResourceUtils.resolve(createEntry.getContainer(), "private/implementation/service.xml"), 
											"rest-crud/create.xml",
											type.getId(), 
											name + "Id",
											collectionName
										);
									}
									
									RepositoryEntry updateEntry = (RepositoryEntry) entry.getChild("update");
									if (updateEntry == null) {
										updateEntry = ((RepositoryEntry) entry).createNode("update", restManager, true);
										RESTService update = guiManager.newInstance(MainController.getInstance(), updateEntry);
										RESTInterfaceArtifact iface = update.getArtifact("api");
										iface.getConfig().setMethod(WebMethod.PUT);
										iface.getConfig().setPath("/" + name + "/{" + name + "Id}");
										iface.getConfig().setInput(inputStructure);
										iface.getConfig().setRoles(Arrays.asList("$user"));
										iface.getConfig().setPermissionContext("=input/path/" + name + "Id");
										iface.getConfig().setPermissionAction(name + ".update");
										// update the id field in the path if possible
										if (primaryKeyField != null) {
											Structure path = iface.getPath();
											path.add(new SimpleElementImpl(name + "Id", (SimpleType) primaryKeyField.getType(), path));
										}
										restManager.save(updateEntry, update);
										writeResource(
											(WritableResource) ResourceUtils.resolve(updateEntry.getContainer(), "private/implementation/service.xml"), 
											"rest-crud/update.xml",
											type.getId(),
											name + "Id",
											collectionName
										);
									}
									
									RepositoryEntry deleteEntry = (RepositoryEntry) entry.getChild("delete");
									if (deleteEntry == null) {
										deleteEntry = ((RepositoryEntry) entry).createNode("delete", restManager, true);
										RESTService delete = guiManager.newInstance(MainController.getInstance(), deleteEntry);
										RESTInterfaceArtifact iface = delete.getArtifact("api");
										iface.getConfig().setMethod(WebMethod.DELETE);
										iface.getConfig().setPath("/" + name + "/{" + name + "Id}");
										iface.getConfig().setRoles(Arrays.asList("$user"));
										iface.getConfig().setPermissionContext("=input/path/" + name + "Id");
										iface.getConfig().setPermissionAction(name + ".delete");
										// update the id field in the path if possible
										if (primaryKeyField != null) {
											Structure path = iface.getPath();
											path.add(new SimpleElementImpl(name + "Id", (SimpleType) primaryKeyField.getType(), path));
										}
										restManager.save(deleteEntry, delete);
										writeResource(
											(WritableResource) ResourceUtils.resolve(deleteEntry.getContainer(), "private/implementation/service.xml"), 
											"rest-crud/delete.xml",
											type.getId(),
											name + "Id",
											collectionName
										);
									}
										
									RepositoryEntry listRestEntry = (RepositoryEntry) entry.getChild("list");
									if (listRestEntry == null) {
										listRestEntry = ((RepositoryEntry) entry).createNode("list", restManager, true);
										RESTService list = guiManager.newInstance(MainController.getInstance(), listRestEntry);
										RESTInterfaceArtifact iface = list.getArtifact("api");
										iface.getConfig().setMethod(WebMethod.GET);
										iface.getConfig().setPath("/" + name);
										iface.getConfig().setOutput(listStructure);
										iface.getConfig().setRoles(Arrays.asList("$user"));
										iface.getConfig().setPermissionAction(name + ".list");
										iface.getConfig().setQueryParameters("orderBy, limit, offset");
										Structure query = iface.getQuery();
										query.add(new SimpleElementImpl<String>("orderBy", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(String.class), query,
											new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
											new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)
										));
										query.add(new SimpleElementImpl<Long>("limit", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class), query,
											new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
											new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 1)
										));
										query.add(new SimpleElementImpl<Long>("offset", SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(Long.class), query,
											new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
											new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 1)
										));
										// allow for ids to be passed in as an array, for resolving
										if (primaryKeyField != null) {
											iface.getConfig().setQueryParameters(iface.getConfig().getQueryParameters() + ", " + primaryKeyField.getName());
											query.add(new SimpleElementImpl(primaryKeyField.getName(), (SimpleType) primaryKeyField.getType(), query,
												new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
												new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 0)
											));
										}
										for (Element<?> child : TypeUtils.getAllChildren(inputStructure)) {
											if (!child.getName().equals("orderBy") && !child.getName().equals("limit") && !child.getName().equals("offset")) {
												// add it to the query
												iface.getConfig().setQueryParameters(iface.getConfig().getQueryParameters() + ", " + child.getName());
												query.add(new SimpleElementImpl(child.getName(), (SimpleType) child.getType(), query,
													new ValueImpl<Integer>(MinOccursProperty.getInstance(), 0),
													new ValueImpl<Integer>(MaxOccursProperty.getInstance(), 1)
												));
											}
										}
										restManager.save(listRestEntry, list);
										writeResource(
											(WritableResource) ResourceUtils.resolve(listRestEntry.getContainer(), "private/implementation/service.xml"), 
											"rest-crud/list.xml",
											type.getId(),
											name + "Id",
											collectionName
										);
									}
									
									RepositoryEntry getEntry = (RepositoryEntry) entry.getChild("get");
									if (getEntry == null) {
										getEntry = ((RepositoryEntry) entry).createNode("get", restManager, true);
										RESTService get = guiManager.newInstance(MainController.getInstance(), getEntry);
										RESTInterfaceArtifact iface = get.getArtifact("api");
										iface.getConfig().setMethod(WebMethod.GET);
										iface.getConfig().setPath("/" + name + "/{" + name + "Id}");
										iface.getConfig().setOutput(outputStructure);
										iface.getConfig().setRoles(Arrays.asList("$user"));
										iface.getConfig().setPermissionContext("=input/path/" + name + "Id");
										iface.getConfig().setPermissionAction(name + ".get");
										// update the id field in the path if possible
										if (primaryKeyField != null) {
											Structure path = iface.getPath();
											path.add(new SimpleElementImpl(name + "Id", (SimpleType) primaryKeyField.getType(), path));
										}
										restManager.save(getEntry, get);
										writeResource(
											(WritableResource) ResourceUtils.resolve(getEntry.getContainer(), "private/implementation/service.xml"), 
											"rest-crud/get.xml",
											type.getId(),
											name + "Id",
											collectionName
										);
									}
									
									TreeItem<Entry> parentTreeItem = MainController.getInstance().getRepositoryBrowser().getControl().resolve(entry.getId().replace(".", "/"));
									// @optimize
									if (parentTreeItem != null) {
										MainController.getInstance().getRepositoryBrowser().getControl().getTreeCell(parentTreeItem).refresh();
									}
									else {
										MainController.getInstance().getRepositoryBrowser().refresh();
									}
									
									// reload stuff
									MainController.getInstance().getAsynchronousRemoteServer().reload(entry.getId());
									MainController.getInstance().getCollaborationClient().created(inputEntry.getId(), "Generated");
									MainController.getInstance().getCollaborationClient().created(outputEntry.getId(), "Generated");
									MainController.getInstance().getCollaborationClient().created(listEntry.getId(), "Generated");
									MainController.getInstance().getCollaborationClient().created(createEntry.getId(), "Generated");
									MainController.getInstance().getCollaborationClient().created(updateEntry.getId(), "Generated");
									MainController.getInstance().getCollaborationClient().created(getEntry.getId(), "Generated");
								}
								catch (Exception e) {
									MainController.getInstance().notify(e);
								}
							}
						}

					});
				}
			});
			return item;
		}
		return null;
	}
	
	private void writeResource(WritableResource service, String name, String typeId, String idField, String listField) throws IOException {
		WritableContainer<ByteBuffer> writable = service.getWritable();
		try {
			InputStream input = new BufferedInputStream(Thread.currentThread().getContextClassLoader().getResourceAsStream(name));
			try {
				byte[] bytes = IOUtils.toBytes(IOUtils.wrap(input));
				String content = new String(bytes, "UTF-8");
				if (typeId != null) {
					content = content.replace("${typeId}", typeId);
				}
				if (idField != null) {
					content = content.replace("${idField}", idField);
				}
				if (listField != null) {
					content = content.replace("${listField}", listField);
				}
				writable.write(IOUtils.wrap(content.getBytes("UTF-8"), true));
			}
			finally {
				input.close();
			}
		}
		finally {
			writable.close();
		}
	}
}
