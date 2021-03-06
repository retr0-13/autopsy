/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.event.ListSelectionListener;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * A JPanel to display video thumbnails.
 *
 */
final class VideoThumbnailViewer extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private final DefaultListModel<VideoThumbnailsWrapper> thumbnailListModel = new DefaultListModel<>();

    /**
     * Creates new form VideoThumbnailViewer.
     */
    VideoThumbnailViewer() {
        initComponents();
    }

    /**
     * Add a selection listener to the list of thumbnails being displayed.
     *
     * @param listener The ListSelectionListener to add to the selection model.
     */
    void addListSelectionListener(ListSelectionListener listener) {
        thumbnailList.getSelectionModel().addListSelectionListener(listener);
    }

    /**
     * Get the list of AbstractFiles which are represented by the selected Video
     * thumbnails.
     *
     * @return The list of AbstractFiles which are represented by the selected
     *         Video thumbnails.
     */
    List<AbstractFile> getInstancesForSelected() {
        synchronized (this) {
            if (thumbnailList.getSelectedIndex() == -1) {
                return new ArrayList<>();
            } else {
                return thumbnailListModel.getElementAt(thumbnailList.getSelectedIndex()).getResultFile().getAllInstances();
            }
        }
    }

    /**
     * Clear the list of thumbnails being displayed.
     */
    void clearViewer() {
        synchronized (this) {
            thumbnailListModel.removeAllElements();
            thumbnailListScrollPane.getVerticalScrollBar().setValue(0);
        }
    }

    /**
     * Add thumbnails for a video to the panel.
     *
     * @param thumbnailWrapper The object which contains the thumbnails which
     *                         will be displayed.
     */
    void addVideo(VideoThumbnailsWrapper thumbnailWrapper) {
        synchronized (this) {
            thumbnailListModel.addElement(thumbnailWrapper);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        thumbnailListScrollPane = new javax.swing.JScrollPane();
        thumbnailList = new javax.swing.JList<>();

        setLayout(new java.awt.BorderLayout());

        thumbnailList.setModel(thumbnailListModel);
        thumbnailList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        thumbnailList.setCellRenderer(new org.sleuthkit.autopsy.discovery.ui.VideoThumbnailPanel());
        thumbnailListScrollPane.setViewportView(thumbnailList);

        add(thumbnailListScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<org.sleuthkit.autopsy.discovery.ui.VideoThumbnailsWrapper> thumbnailList;
    private javax.swing.JScrollPane thumbnailListScrollPane;
    // End of variables declaration//GEN-END:variables

}
