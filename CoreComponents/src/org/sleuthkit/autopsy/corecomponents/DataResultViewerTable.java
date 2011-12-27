/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.OutlineView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Node.Property;
import org.openide.nodes.Node.PropertySet;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;

/**
 * DataResult sortable table viewer
 */
@ServiceProvider(service = DataResultViewer.class)
public class DataResultViewerTable extends AbstractDataResultViewer {

    private transient ExplorerManager em = new ExplorerManager();
    private String firstColumnLabel = "Name";

    /** Creates new form DataResultViewerTable */
    public DataResultViewerTable() {
        initComponents();

        OutlineView ov = ((OutlineView) this.tableScrollPanel);

        // only allow one item to be selected at a time
        ov.getOutline().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // don't show the root node
        ov.getOutline().setRootVisible(false);

        this.em.addPropertyChangeListener(this);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tableScrollPanel = new OutlineView(this.firstColumnLabel);

        //new TreeTableView()
        tableScrollPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                tableScrollPanelComponentResized(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tableScrollPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 691, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tableScrollPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 366, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void tableScrollPanelComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_tableScrollPanelComponentResized
    }//GEN-LAST:event_tableScrollPanelComponentResized
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane tableScrollPanel;
    // End of variables declaration//GEN-END:variables

    @Override
    public ExplorerManager getExplorerManager() {
        return this.em;
    }

    @Override
    public Node getSelectedNode() {
        Node result = null;
        Node[] selectedNodes = this.getExplorerManager().getSelectedNodes();
        if (selectedNodes.length > 0) {
            result = selectedNodes[0];
        }
        return result;
    }

    /**
     * Gets regular Bean property set properties from first child of Node.
     * @param parent Node with at least one child to get properties from
     * @return Properties,
     */
    private Node.Property[] getChildPropertyHeaders(Node parent) {
        Node firstChild = parent.getChildren().getNodeAt(0);

        if (firstChild == null) {
            throw new IllegalArgumentException("Couldn't get a child Node from the given parent.");
        } else {
            for (PropertySet ps : firstChild.getPropertySets()) {
                if (ps.getName().equals(Sheet.PROPERTIES)) {
                    return ps.getProperties();
                }
            }

            throw new IllegalArgumentException("Child Node doesn't have the regular PropertySet.");
        }
    }

    @Override
    public void setNode(Node selectedNode) {
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            boolean hasChildren = false;


            if (selectedNode != null) {
                hasChildren = selectedNode.getChildren().getNodesCount() > 0;
            }


            // if there's no selection node, do nothing
            if (hasChildren) {
                Node root = selectedNode;

                //wrap to filter out children
                //note: this breaks the tree view mode in this generic viewer,
                //so wrap nodes earlier if want 1 level view
                //if (!(root instanceof TableFilterNode)) {
                ///    root = new TableFilterNode(root, true);
                //}

                em.setRootContext(root);

                OutlineView ov = ((OutlineView) this.tableScrollPanel);

                List<Node.Property> tempProps = new ArrayList<Node.Property>(Arrays.asList(getChildPropertyHeaders(selectedNode)));

                tempProps.remove(0);

                Node.Property[] props = tempProps.toArray(new Node.Property[tempProps.size()]);


                // *********** Make the TreeTableView to be sortable ***************

                //First property column is sortable, but also sorted initially, so
                //initially this one will have the arrow icon:
                props[0].setValue("TreeColumnTTV", Boolean.TRUE); // Identifies special property representing first (tree) column.
                props[0].setValue("ComparableColumnTTV", Boolean.TRUE); // This property column should be used for sorting.
                props[0].setValue("SortingColumnTTV", Boolean.TRUE); // TreeTableView should be initially sorted by this property column.

                // The rest of the columns are sortable, but not initially sorted,
                // so initially will have no arrow icon:
                for (int i = 1; i < props.length; i++) {
                    props[i].setValue("ComparableColumnTTV", Boolean.TRUE);
                }

                // *****************************************************************

                //ttv.setProperties(props); // set the properties
                ov.setProperties(props); // set the properties

                //            // set the first entry
                //            Children test = root.getChildren();
                //            Node firstEntryNode = test.getNodeAt(0);
                //            try {
                //                this.getExplorerManager().setSelectedNodes(new Node[]{firstEntryNode});
                //            } catch (PropertyVetoException ex) {}


                // show the horizontal scroll panel and show all the content & header

                int totalColumns = props.length;

                //int scrollWidth = ttv.getWidth();
                int scrollWidth = ov.getWidth();
                int minWidth = scrollWidth / totalColumns;
                int margin = 4;
                int startColumn = 1;
                ov.getOutline().setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

                // get the fontmetrics
                //FontMetrics metrics = ttv.getGraphics().getFontMetrics();
                FontMetrics metrics = ov.getGraphics().getFontMetrics();

                // get first 100 rows values for the table
                Object[][] content = null;
                content = getRowValues(selectedNode, 100);


                if (content != null) {
                    // for the "Name" column
                    int nodeColWidth = getMaxColumnWidth(0, metrics, margin, 40, firstColumnLabel, content); // Note: 40 is the width of the icon + node lines. Change this value if those values change!
                    ov.getOutline().getColumnModel().getColumn(0).setPreferredWidth(nodeColWidth);

                    // get the max for each other column
                    for (int colIndex = startColumn; colIndex < totalColumns; colIndex++) {
                        int colWidth = getMaxColumnWidth(colIndex, metrics, margin, 8, props, content);
                        ov.getOutline().getColumnModel().getColumn(colIndex).setPreferredWidth(colWidth);
                    }
                }

                // if there's no content just auto resize all columns
                if (!(content.length > 0)) {
                    // turn on the auto resize
                    ov.getOutline().setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                }

            } else {
                Node emptyNode = new AbstractNode(Children.LEAF);
                em.setRootContext(emptyNode); // make empty node
                ((OutlineView) this.tableScrollPanel).getOutline().setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                ((OutlineView) this.tableScrollPanel).setProperties(new Node.Property[]{}); // set the empty property header
            }
        } finally {
            this.setCursor(null);
        }
    }
    
    
    private static Object[][] getRowValues(Node node, int rows) {
        // how many rows are we returning
        int maxRows = Math.min(rows, node.getChildren().getNodesCount());
        
        Object[][] objs = new Object[maxRows][];

        for (int i = 0; i < maxRows; i++) {
            PropertySet[] props = node.getChildren().getNodeAt(i).getPropertySets();
            Property[] property = props[0].getProperties();
            objs[i] = new Object[property.length];


            for (int j = 0; j < property.length; j++) {
                try {
                    objs[i][j] = property[j].getValue();
                } catch (IllegalAccessException ignore) {
                    objs[i][j] = "n/a";
                } catch (InvocationTargetException ignore) {
                    objs[i][j] = "n/a";
                }            
            }
        }
        return objs;
    }

    @Override
    public String getTitle() {
        return "Table View";
    }

    @Override
    public DataResultViewer getInstance() {
        return new DataResultViewerTable();
    }

    @Override
    public void resetComponent() {
    }

    /**
     * Gets the max width of the column from the given index, header, and table.
     *
     * @param index    the index of the column on the table / header
     * @param metrics  the font metrics that this component use
     * @param margin   the left/right margin of the column
     * @param padding  the left/right padding of the column
     * @param header   the property headers of the table
     * @param table    the object table
     * @return max  the maximum width of the column
     */
    private int getMaxColumnWidth(int index, FontMetrics metrics, int margin, int padding, Node.Property[] header, Object[][] table) {
        // set the tree (the node / names column) width
        String headerName = header[index - 1].getDisplayName();

        return getMaxColumnWidth(index, metrics, margin, padding, headerName, table);
    }

    /**
     * Gets the max width of the column from the given index, header, and table.
     *
     * @param index    the index of the column on the table / header
     * @param metrics  the font metrics that this component use
     * @param margin   the left/right margin of the column
     * @param padding  the left/right padding of the column
     * @param header   the column header for the comparison
     * @param table    the object table
     * @return max  the maximum width of the column
     */
    private int getMaxColumnWidth(int index, FontMetrics metrics, int margin, int padding, String header, Object[][] table) {
        // set the tree (the node / names column) width
        String headerName = header;
        int headerWidth = metrics.stringWidth(headerName); // length of the header
        int colWidth = 0;

        // Get maximum width of column data
        for (int i = 0; i < table.length; i++) {
            String test = table[i][index].toString();
            colWidth = Math.max(colWidth, metrics.stringWidth(test));
        }

        colWidth += padding; // add the padding on the most left gap
        headerWidth += 8; // add the padding to the header (change this value if the header padding value is changed)

        // Set the width
        int width = Math.max(headerWidth, colWidth);
        width += 2 * margin; // Add margin

        return width;
    }

    @Override
    public void clearComponent() {
        em.removePropertyChangeListener(this);
        this.tableScrollPanel.removeAll();
        this.tableScrollPanel = null;
        try {
            this.em.getRootContext().destroy();
            em = null;
        } catch (IOException ex) {
            // TODO: Proper thing to do? Log? Don't throw RuntimeException?
            throw new RuntimeException("Error: can't clear the component of the Table Result Viewer.", ex);
        }
    }

    @Override
    public Component getComponent() {
        return this;
    }
}
