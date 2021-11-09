/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.mainui.nodes;

import java.util.List;
import java.util.Optional;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.sleuthkit.autopsy.datamodel.LayoutFileNode;
import org.sleuthkit.autopsy.datamodel.LocalDirectoryNode;
import org.sleuthkit.autopsy.datamodel.LocalFileNode;
import org.sleuthkit.autopsy.datamodel.SlackFileNode;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import org.sleuthkit.autopsy.mainui.datamodel.ArtifactRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionContext;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.VirtualDirectory;

public abstract class ArtifactNode<T extends BlackboardArtifact, R extends ArtifactRowDTO<T>> extends AbstractNode implements ActionContext {

    private final R rowData;
    private final BlackboardArtifact.Type artifactType;
    private final List<ColumnKey> columns;
    private Node parentFileNode;

    ArtifactNode(R rowData, List<ColumnKey> columns, BlackboardArtifact.Type artifactType, Lookup lookup, String iconPath) {
        super(Children.LEAF, lookup);
        this.rowData = rowData;
        this.artifactType = artifactType;
        this.columns = columns;
        setupNodeDisplay(iconPath);
    }

    @Override
    public Optional<Content> getSourceContent() {
        return Optional.ofNullable(rowData.getSrcContent());
    }

    @Override
    public Optional<AbstractFile> getLinkedFile() {
        return Optional.ofNullable((AbstractFile) rowData.getLinkedFile());
    }

    @Override
    public boolean supportsViewInTimeline() {
        return rowData.isTimelineSupported();
    }

    @Override
    public Optional<BlackboardArtifact> getArtifactForTimeline() {
        return Optional.ofNullable(rowData.getArtifact());
    }

    @Override
    public boolean supportsAssociatedFileActions() {
        return getLinkedFile().isPresent();
    }

    @Override
    public boolean supportsSourceContentActions() {
        Content sourceContent = rowData.getSrcContent();

        return (sourceContent instanceof DataArtifact)
                || (sourceContent instanceof OsAccount)
                || (sourceContent instanceof AbstractFile || (rowData.getArtifact() instanceof DataArtifact));
    }

    @Override
    public Optional<AbstractFile> getSourceFileForTimelineAction() {
        return Optional.ofNullable(rowData.getSrcContent() instanceof AbstractFile ? (AbstractFile) rowData.getSrcContent() : null);
    }

    @Override
    public Optional<BlackboardArtifact> getArtifact() {
        return Optional.of(rowData.getArtifact());
    }

    @Override
    public boolean supportsSourceContentViewerActions() {
        return rowData.getSrcContent() != null;
    }

    @Override
    public Optional<Node> getNewWindowActionNode() {
        return Optional.ofNullable(getParentFileNode());
    }

    @Override
    public Optional<Node> getExternalViewerActionNode() {
        return Optional.ofNullable(getParentFileNode());
    }

    @Override
    public boolean supportsExtractActions() {
        return rowData.getSrcContent() instanceof AbstractFile;
    }

    @Override
    public boolean supportsArtifactTagAction() {
        return true;
    }
    
    private Node getParentFileNode() {
        if (parentFileNode == null) {
            parentFileNode = getParentFileNode(rowData.getSrcContent());
        }
        return parentFileNode;
    }

    protected void setupNodeDisplay(String iconPath) {
        // use first cell value for display name
        String displayName = rowData.getCellValues().size() > 0
                ? rowData.getCellValues().get(0).toString()
                : "";

        setDisplayName(displayName);
        setShortDescription(displayName);
        setName(Long.toString(rowData.getId()));
        setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
    }

    @Override
    protected Sheet createSheet() {
        return ContentNodeUtil.setSheet(super.createSheet(), columns, rowData.getCellValues());
    }

    @Override
    public Action[] getActions(boolean context) {
        return ActionsFactory.getActions( this);
    }

    /**
     * Returns a Node representing the file content if the content is indeed
     * some sort of file. Otherwise, return null.
     *
     * @param content The content.
     *
     * @return The file node or null if not a file.
     */
    private Node getParentFileNode(Content content) {
        if (content instanceof File) {
            return new org.sleuthkit.autopsy.datamodel.FileNode((AbstractFile) content);
        } else if (content instanceof Directory) {
            return new DirectoryNode((Directory) content);
        } else if (content instanceof VirtualDirectory) {
            return new VirtualDirectoryNode((VirtualDirectory) content);
        } else if (content instanceof LocalDirectory) {
            return new LocalDirectoryNode((LocalDirectory) content);
        } else if (content instanceof LayoutFile) {
            return new LayoutFileNode((LayoutFile) content);
        } else if (content instanceof LocalFile || content instanceof DerivedFile) {
            return new LocalFileNode((AbstractFile) content);
        } else if (content instanceof SlackFile) {
            return new SlackFileNode((AbstractFile) content);
        } else {
            return null;
        }
    }
}