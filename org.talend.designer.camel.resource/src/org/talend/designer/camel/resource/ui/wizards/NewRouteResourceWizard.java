// ============================================================================
//
// Copyright (C) 2006-2012 Talend Inc. - www.talend.com
//
// This source code is available under agreement available at
// %InstallDIR%\features\org.talend.rcp.branding.%PRODUCTNAME%\%PRODUCTNAME%license.txt
//
// You should have received a copy of the agreement
// along with this program; if not, write to Talend SA
// 9 rue Pages 92150 Suresnes, France
//
// ============================================================================
package org.talend.designer.camel.resource.ui.wizards;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.wizard.Wizard;
import org.talend.camel.core.model.camelProperties.CamelPropertiesFactory;
import org.talend.camel.core.model.camelProperties.RouteResourceItem;
import org.talend.commons.exception.LoginException;
import org.talend.commons.exception.PersistenceException;
import org.talend.commons.ui.runtime.exception.ExceptionHandler;
import org.talend.commons.ui.runtime.exception.MessageBoxExceptionHandler;
import org.talend.commons.ui.runtime.image.ECoreImage;
import org.talend.commons.ui.runtime.image.ImageProvider;
import org.talend.commons.utils.VersionUtils;
import org.talend.core.CorePlugin;
import org.talend.core.context.Context;
import org.talend.core.context.RepositoryContext;
import org.talend.core.model.properties.ByteArray;
import org.talend.core.model.properties.PropertiesFactory;
import org.talend.core.model.properties.Property;
import org.talend.core.model.properties.ReferenceFileItem;
import org.talend.core.model.relationship.RelationshipItemBuilder;
import org.talend.core.repository.constants.FileConstants;
import org.talend.designer.core.DesignerPlugin;
import org.talend.repository.RepositoryWorkUnit;
import org.talend.repository.model.IProxyRepositoryFactory;

/**
 * Wizard for the creation of a new Route Resource. <br/>
 * 
 * @author xpli
 * 
 */
public class NewRouteResourceWizard extends Wizard {

	/** Main page. */
	private NewRouteResourceWizardPage mainPage;

	private RouteResourceItem item;

	private Property property;

	private IPath path;

	private IProxyRepositoryFactory repositoryFactory;

	private IPath filePath;

	/**
	 * Constructs a new NewProjectWizard.
	 * 
	 * @param author
	 *            Project author.
	 * @param server
	 * @param password
	 */
	public NewRouteResourceWizard(IPath path) {
		super();
		this.path = path;

		this.property = PropertiesFactory.eINSTANCE.createProperty();
		this.property.setAuthor(((RepositoryContext) CorePlugin.getContext()
				.getProperty(Context.REPOSITORY_CONTEXT_KEY)).getUser());
		this.property.setVersion(VersionUtils.DEFAULT_VERSION);
		this.property.setStatusCode(""); //$NON-NLS-1$

		item = CamelPropertiesFactory.eINSTANCE.createRouteResourceItem();

		item.setProperty(property);

		repositoryFactory = DesignerPlugin.getDefault().getRepositoryService()
				.getProxyRepositoryFactory();

		setDefaultPageImageDescriptor(ImageProvider
				.getImageDesc(ECoreImage.DEFAULT_WIZ));
	}

	/**
	 * @see org.eclipse.jface.wizard.Wizard#addPages()
	 */
	@Override
	public void addPages() {
		mainPage = new NewRouteResourceWizardPage(property, path);
		addPage(mainPage);
		setWindowTitle("New Route Resource"); //$NON-NLS-1$
	}

	/**
	 * Getter for docFilePath.
	 * 
	 * @return the docFilePath
	 */
	public IPath getFilePath() {
		return this.filePath;
	}

	/**
	 * Getter for project.
	 * 
	 * @return the project
	 */
	public RouteResourceItem getItem() {
		return this.item;
	}

	/**
	 * @see org.eclipse.jface.wizard.Wizard#performFinish()
	 */
	@Override
	public boolean performFinish() {
		property.setId(repositoryFactory.getNextId());
		property.setLabel(property.getDisplayName());
		
		URL url = mainPage.getUrl();
		Path p = new Path(property.getLabel());
		String itemName = p.removeFileExtension().lastSegment();
		String fileExtension = null;
		if (url != null) {
			p = new Path(url.getPath());
			if (p.getFileExtension() != null) {
				fileExtension = p.getFileExtension();
			}
		} else {
			fileExtension = p.getFileExtension();
		}

		// https://jira.talendforge.org/browse/TESB-6853
		if (fileExtension == null || fileExtension.isEmpty()) {
			fileExtension = "txt";
		}

		// In case the source file extension is "item"
		if (fileExtension.equals(FileConstants.ITEM_EXTENSION)) {
			fileExtension += "_";
		}

		// In case the source file extension is "properties"
		if (fileExtension.equals(FileConstants.PROPERTIES_EXTENSION)) {
			fileExtension += "_";
		}

		ByteArray byteArray = PropertiesFactory.eINSTANCE.createByteArray();
		StringBuffer sb = new StringBuffer();
		if (url == null) {
			 byteArray.setInnerContent(new byte[0]);
		} else {
			try {
				InputStream inputStream = url.openStream();
				BufferedReader bufferedReader = new BufferedReader(
						new InputStreamReader(inputStream, "utf-8"));
				String line = bufferedReader.readLine();
				while (line != null) {
					sb.append(line).append(System.getProperty("line.separator"));
					line = bufferedReader.readLine();
				}
				byteArray.setInnerContent(sb.toString().getBytes());
			} catch (Exception e) {
				MessageBoxExceptionHandler.process(e);
				ExceptionHandler.process(e);
				return false;
			}
		}

		ReferenceFileItem refItem = PropertiesFactory.eINSTANCE
				.createReferenceFileItem();
		refItem.setContent(byteArray);
		refItem.setExtension(fileExtension);
		refItem.setName(itemName);

		item.setName(itemName);

		item.setBindingExtension(fileExtension);
		byteArray = PropertiesFactory.eINSTANCE.createByteArray();
		byteArray.setInnerContent(new byte[0]);
		item.setContent(byteArray);
		item.getReferenceResources().add(refItem);

		RepositoryWorkUnit<Object> workUnit = new RepositoryWorkUnit<Object>(
				this.getWindowTitle(), this) {
			@Override
			protected void run() throws LoginException, PersistenceException {
				repositoryFactory.create(item, mainPage.getDestinationPath());
				RelationshipItemBuilder.getInstance().addOrUpdateItem(item);
			}
		};
		workUnit.setAvoidUnloadResources(true);
		repositoryFactory.executeRepositoryWorkUnit(workUnit);

		return item != null;
	}

	public void setFilePath(IPath filePath) {
		this.filePath = filePath;
	}
}
