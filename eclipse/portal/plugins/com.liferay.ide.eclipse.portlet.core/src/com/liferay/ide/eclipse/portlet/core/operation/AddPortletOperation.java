/*******************************************************************************
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 *******************************************************************************/

package com.liferay.ide.eclipse.portlet.core.operation;

import com.liferay.ide.eclipse.core.util.CoreUtil;
import com.liferay.ide.eclipse.portlet.core.PortletCore;
import com.liferay.ide.eclipse.portlet.core.dd.PortletDescriptorHelper;
import com.liferay.ide.eclipse.project.core.IPluginWizardFragmentProperties;
import com.liferay.ide.eclipse.project.core.util.ProjectUtil;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.templates.DocumentTemplateContext;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateBuffer;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.jface.text.templates.persistence.TemplateStore;
import org.eclipse.jst.j2ee.common.ParamValue;
import org.eclipse.jst.j2ee.internal.common.operations.AddJavaEEArtifactOperation;
import org.eclipse.jst.j2ee.internal.common.operations.NewJavaEEArtifactClassOperation;
import org.eclipse.wst.common.componentcore.ComponentCore;
import org.eclipse.wst.common.componentcore.resources.IVirtualFolder;
import org.eclipse.wst.common.frameworks.datamodel.IDataModel;

/**
 * @author Greg Amerson
 */
@SuppressWarnings("restriction")
public class AddPortletOperation extends AddJavaEEArtifactOperation
	implements INewPortletClassDataModelProperties, IPluginWizardFragmentProperties {

	protected IVirtualFolder docroot;

	protected TemplateContextType portletContextType;

	protected TemplateStore templateStore;

	public AddPortletOperation(IDataModel dataModel) {
		super(dataModel);

		this.docroot = ComponentCore.createComponent(getTargetProject()).getRootFolder();
		this.templateStore = (TemplateStore) getDataModel().getProperty(TEMPLATE_STORE);
		this.portletContextType = (TemplateContextType) getDataModel().getProperty(CONTEXT_TYPE);
	}

	@Override
	public IStatus doExecute(IProgressMonitor monitor, IAdaptable info)
		throws ExecutionException {

		IStatus status = Status.OK_STATUS;

		if ( getDataModel().getBooleanProperty( CREATE_NEW_PORTLET_CLASS ) ) {
			status = super.doExecute( monitor, info );
		}

		if (!status.isOK()) {
			return status;
		}

		if (getDataModel().getBooleanProperty(CREATE_RESOURCE_BUNDLE_FILE)) {
			try {
				createEmptyFileInDefaultSourceFolder(getDataModel().getStringProperty(CREATE_RESOURCE_BUNDLE_FILE_PATH));
			}
			catch (CoreException e) {
				status = PortletCore.createErrorStatus(e);
			}
		}

        if (getDataModel().getBooleanProperty( CREATE_ENTRY_CLASS ) && getDataModel().getBooleanProperty( ADD_TO_CONTROL_PANEL )) {
            try {
                NewEntryClassOperation entryClassOperation = new NewEntryClassOperation(getDataModel());
                entryClassOperation.execute( monitor, info );
            }
            catch (ExecutionException e) {
                status = PortletCore.createErrorStatus( e );
            }
        }

		if (getDataModel().getBooleanProperty(CREATE_JSPS)) {
			status = createModeJSPFiles();
		}

		try {
			String cssFilePath = getDataModel().getStringProperty(CSS_FILE);

			if (!CoreUtil.isNullOrEmpty(cssFilePath)) {
				createEmptyFileInDocroot(cssFilePath);
			}

			String javascriptFilePath = getDataModel().getStringProperty(JAVASCRIPT_FILE);

			if (!CoreUtil.isNullOrEmpty(javascriptFilePath)) {
				createEmptyFileInDocroot(javascriptFilePath);
			}
		}
		catch (Exception ex) {
			status = PortletCore.createErrorStatus(ex);
		}

		return status;
	}

	@Override
	public IStatus execute(final IProgressMonitor monitor, final IAdaptable info)
		throws ExecutionException {

		IStatus status = doExecute(monitor, info);

		if (!status.isOK()) {
			return status;
		}

		generateMetaData(getDataModel());

		return Status.OK_STATUS;
	}

	protected void createEmptyFileInDefaultSourceFolder(String filePath)
		throws CoreException {

		IFolder[] sourceFolders = ProjectUtil.getSourceFolders(getTargetProject());

		if (sourceFolders != null && sourceFolders.length > 0) {
			IFile projectFile = sourceFolders[0].getFile(filePath);

			if (!projectFile.exists()) {
				IFolder parent = (IFolder) projectFile.getParent();

				CoreUtil.prepareFolder(parent);

				projectFile.create(new ByteArrayInputStream(new byte[0]), IResource.FORCE, null);
			}
		}
	}

	protected void createEmptyFileInDocroot(String filePath)
		throws CoreException {

		IFile projectFile = getProjectFile(filePath);

		if (!projectFile.exists()) {
			IFolder parent = (IFolder) projectFile.getParent();

			CoreUtil.prepareFolder(parent);

			projectFile.create(new ByteArrayInputStream(new byte[0]), IResource.FORCE, null);
		}

	}

	@SuppressWarnings( "unchecked" )
    protected IStatus createModeJSPFiles() {
		IDataModel dm = getDataModel();

		TemplateContext context = new DocumentTemplateContext(portletContextType, new Document(), 0, 0);
		context.setVariable("portlet_display_name", getDataModel().getStringProperty(DISPLAY_NAME));

        List<ParamValue> initParams = (List<ParamValue>) getDataModel().getProperty( INIT_PARAMS );

        String initParamSuffix = null;

        if( initNames61[0].equals( initParams.get( 0 ).getName() ) )
        {
            initParamSuffix = "template";
        }
        else
        {
            initParamSuffix = "jsp";
        }

		if (dm.getBooleanProperty(ABOUT_MODE)) {
			createResourceForMode("about-" + initParamSuffix, ABOUT_MODE_TEMPLATE, context);
		}

		if (dm.getBooleanProperty(CONFIG_MODE)) {
			createResourceForMode("config-" + initParamSuffix, CONFIG_MODE_TEMPLATE, context);
		}

		if (dm.getBooleanProperty(EDIT_MODE)) {
			createResourceForMode("edit-" + initParamSuffix, EDIT_MODE_TEMPLATE, context);
		}

		if (dm.getBooleanProperty(EDITDEFAULTS_MODE)) {
			createResourceForMode("edit-defaults-" + initParamSuffix, EDITDEFAULTS_MODE_TEMPLATE, context);
		}

		if (dm.getBooleanProperty(EDITGUEST_MODE)) {
			createResourceForMode("edit-guest-" + initParamSuffix, EDITGUEST_MODE_TEMPLATE, context);
		}

		if (dm.getBooleanProperty(HELP_MODE)) {
			createResourceForMode("help-" + initParamSuffix, HELP_MODE_TEMPLATE, context);
		}

		if (dm.getBooleanProperty(PREVIEW_MODE)) {
			createResourceForMode("preview-" + initParamSuffix, PREVIEW_MODE_TEMPLATE, context);
		}

		if (dm.getBooleanProperty(PRINT_MODE)) {
			createResourceForMode("print-" + initParamSuffix, PRINT_MODE_TEMPLATE, context);
		}

		if (dm.getBooleanProperty(VIEW_MODE)) {
			createResourceForMode("view-" + initParamSuffix, VIEW_MODE_TEMPLATE, context);
		}

		return Status.OK_STATUS;
	}

	protected PortletDescriptorHelper createPortletDescriptorHelper(IProject targetProject) {
		return new PortletDescriptorHelper(targetProject);
	}

	@SuppressWarnings("unchecked")
	protected void createResourceForMode(String initParamName, String templateId, TemplateContext context) {
		Template template = templateStore.findTemplateById(templateId);

		String templateString = null;
		try {
			TemplateBuffer buffer = context.evaluate(template);

			templateString = buffer.getString();
		}
		catch (Exception ex) {
			PortletCore.logError(ex);

			return;
		}

		// need to get the path in docroot for the new jsp mode file
		IFile viewJspFile = null;

		List<ParamValue> initParams = (List<ParamValue>) getDataModel().getProperty(INIT_PARAMS);

		for (ParamValue paramValue : initParams) {
			if (paramValue.getName().equals(initParamName)) {
				viewJspFile = getProjectFile(paramValue.getValue());

				break;
			}
		}

		if (viewJspFile != null) {
			try {
				if (viewJspFile.exists()) {
					viewJspFile.setContents(
						new ByteArrayInputStream(templateString.getBytes("UTF-8")), IResource.FORCE, null);
				}
				else {
					// make sure that full path to jspfile is available
					CoreUtil.prepareFolder((IFolder) viewJspFile.getParent());
					viewJspFile.create(
						new ByteArrayInputStream(templateString.getBytes("UTF-8")), IResource.FORCE, null);
				}
			}
			catch (Exception ex) {
				PortletCore.logError(ex);
			}
		}
	}

	protected IStatus generateMetaData(IDataModel aModel) {
		if (shouldGenerateMetaData(aModel)) {
			PortletDescriptorHelper portletDescHelper = createPortletDescriptorHelper(getTargetProject());

			if (aModel.getBooleanProperty(REMOVE_EXISTING_ARTIFACTS)) {
				portletDescHelper.removeAllPortlets();
			}

			IStatus status = portletDescHelper.addNewPortlet(this.model);

			if (!status.isOK()) {
				PortletCore.getDefault().getLog().log(status);
				return status;
			}
		}

		return Status.OK_STATUS;
	}

	@Override
	protected NewJavaEEArtifactClassOperation getNewClassOperation() {
		return new NewPortletClassOperation(getDataModel());
	}

	protected IFile getProjectFile(String filePath) {
		return this.docroot.getFile(filePath).getUnderlyingFile();
	}

	protected boolean shouldGenerateMetaData(IDataModel aModel) {
		return ProjectUtil.isPortletProject(getTargetProject());
	}

}
