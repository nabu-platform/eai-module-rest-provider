package be.nabu.eai.module.rest.provider.iface;

import java.io.IOException;
import java.util.List;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Accordion;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.developer.managers.util.ElementMarshallable;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.developer.util.ElementSelectionListener;
import be.nabu.eai.developer.util.ElementTreeItem;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.jfx.control.tree.Tree;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.base.RootElement;
import be.nabu.libs.types.properties.CollectionFormatProperty;
import be.nabu.libs.types.properties.CommentProperty;
import be.nabu.libs.types.properties.CountryProperty;
import be.nabu.libs.types.properties.FormatProperty;
import be.nabu.libs.types.properties.LanguageProperty;
import be.nabu.libs.types.properties.MaxExclusiveProperty;
import be.nabu.libs.types.properties.MaxInclusiveProperty;
import be.nabu.libs.types.properties.MaxLengthProperty;
import be.nabu.libs.types.properties.MaxOccursProperty;
import be.nabu.libs.types.properties.MinExclusiveProperty;
import be.nabu.libs.types.properties.MinInclusiveProperty;
import be.nabu.libs.types.properties.MinLengthProperty;
import be.nabu.libs.types.properties.MinOccursProperty;
import be.nabu.libs.types.properties.PatternProperty;
import be.nabu.libs.types.properties.TimezoneProperty;
import be.nabu.libs.types.structure.Structure;

public class RESTInterfaceGUIManager extends BaseJAXBGUIManager<RESTInterfaceConfiguration, RESTInterfaceArtifact> {

	public RESTInterfaceGUIManager() {
		super("REST Interface", RESTInterfaceArtifact.class, new RESTInterfaceManager(), RESTInterfaceConfiguration.class);
	}

	@Override
	public String getCategory() {
		return "Protocols";
	}
	
	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected RESTInterfaceArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>...values) throws IOException {
		return new RESTInterfaceArtifact(entry.getName(), entry, entry.getRepository());
	}

	@Override
	public void display(MainController controller, AnchorPane pane, RESTInterfaceArtifact instance) {
		AnchorPane properties = new AnchorPane();
		Accordion displayWithAccordion = super.displayWithAccordion(instance, properties);
//		super.display(instance, properties);
		
		VBox box = new VBox();
		TitledPane titledPane = new TitledPane("Type Definitions", box);
		
		box.getChildren().addAll(
			display(instance, box, instance.getPath()),
			display(instance, box, instance.getQuery(), MinOccursProperty.getInstance(), MaxOccursProperty.getInstance()),
			display(instance, box, instance.getHeader(), MinOccursProperty.getInstance(), MaxOccursProperty.getInstance()),
			display(instance, box, instance.getResponseHeader(), MinOccursProperty.getInstance(), MaxOccursProperty.getInstance()),
			display(instance, box, instance.getCookie(), MinOccursProperty.getInstance(), MaxOccursProperty.getInstance()),
			display(instance, box, instance.getSession(), MinOccursProperty.getInstance(), MaxOccursProperty.getInstance())
		);
		displayWithAccordion.getPanes().add(titledPane);
		
		ScrollPane scroll = new ScrollPane();
		AnchorPane.setBottomAnchor(scroll, 0d);
		AnchorPane.setLeftAnchor(scroll, 0d);
		AnchorPane.setRightAnchor(scroll, 0d);
		AnchorPane.setTopAnchor(scroll, 0d);
//		box.prefWidthProperty().bind(scroll.widthProperty());
		scroll.setFitToWidth(true);
		scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
		scroll.setContent(displayWithAccordion);
		pane.getChildren().add(scroll);
	}
	@SuppressWarnings("rawtypes")
	private Tree<Element<?>> display(RESTInterfaceArtifact instance, VBox box, Structure structure, Property<?>...updatableProperties) {
		ElementSelectionListener elementSelectionListener = new ElementSelectionListener(MainController.getInstance(), false, true, 
			FormatProperty.getInstance(),
			TimezoneProperty.getInstance(),
			CommentProperty.getInstance(),
			MinLengthProperty.getInstance(), 
			MaxLengthProperty.getInstance(), 
			PatternProperty.getInstance(),
			LanguageProperty.getInstance(),
			CountryProperty.getInstance(),
			CollectionFormatProperty.getInstance(),
			new MinInclusiveProperty(),
			new MaxInclusiveProperty(),
			new MinExclusiveProperty(),
			new MaxExclusiveProperty()
		);
		elementSelectionListener.setActualId(getActualId(instance));
		elementSelectionListener.addUpdateableProperties(updatableProperties);
		elementSelectionListener.setForceAllowUpdate(true);
		final Tree<Element<?>> tree = new Tree<Element<?>>(new ElementMarshallable());
		// TODO: introduce locking
		ElementTreeItem.setListeners(tree, new SimpleBooleanProperty(true), true);
		EAIDeveloperUtils.addElementExpansionHandler(tree);
		tree.rootProperty().set(new ElementTreeItem(new RootElement(structure), null, false, false));
		tree.getSelectionModel().selectedItemProperty().addListener(elementSelectionListener);
		tree.prefWidthProperty().bind(box.widthProperty());
		return tree;
	}

}
