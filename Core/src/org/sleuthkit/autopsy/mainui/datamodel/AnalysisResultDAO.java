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
package org.sleuthkit.autopsy.mainui.datamodel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.nodes.DAOFetcher;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.HostAddress;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * DAO for providing data about analysis results to populate the results viewer.
 */
public class AnalysisResultDAO extends BlackboardArtifactDAO {

    private static Logger logger = Logger.getLogger(AnalysisResultDAO.class.getName());

    private static AnalysisResultDAO instance = null;

    @NbBundle.Messages({
        "AnalysisResultDAO.columnKeys.score.name=Score",
        "AnalysisResultDAO.columnKeys.score.displayName=Score",
        "AnalysisResultDAO.columnKeys.score.description=Score",
        "AnalysisResultDAO.columnKeys.conclusion.name=Conclusion",
        "AnalysisResultDAO.columnKeys.conclusion.displayName=Conclusion",
        "AnalysisResultDAO.columnKeys.conclusion.description=Conclusion",
        "AnalysisResultDAO.columnKeys.justification.name=Justification",
        "AnalysisResultDAO.columnKeys.justification.displayName=Justification",
        "AnalysisResultDAO.columnKeys.justification.description=Justification",
        "AnalysisResultDAO.columnKeys.configuration.name=Configuration",
        "AnalysisResultDAO.columnKeys.configuration.displayName=Configuration",
        "AnalysisResultDAO.columnKeys.configuration.description=Configuration",
        "AnalysisResultDAO.columnKeys.sourceType.name=SourceType",
        "AnalysisResultDAO.columnKeys.sourceType.displayName=Source Type",
        "AnalysisResultDAO.columnKeys.sourceType.description=Source Type"
    })
    static final ColumnKey SCORE_COL = new ColumnKey(
            Bundle.AnalysisResultDAO_columnKeys_score_name(),
            Bundle.AnalysisResultDAO_columnKeys_score_displayName(),
            Bundle.AnalysisResultDAO_columnKeys_score_description()
    );

    static final ColumnKey CONCLUSION_COL = new ColumnKey(
            Bundle.AnalysisResultDAO_columnKeys_conclusion_name(),
            Bundle.AnalysisResultDAO_columnKeys_conclusion_displayName(),
            Bundle.AnalysisResultDAO_columnKeys_conclusion_description()
    );

    static final ColumnKey CONFIGURATION_COL = new ColumnKey(
            Bundle.AnalysisResultDAO_columnKeys_configuration_name(),
            Bundle.AnalysisResultDAO_columnKeys_configuration_displayName(),
            Bundle.AnalysisResultDAO_columnKeys_configuration_description()
    );

    static final ColumnKey JUSTIFICATION_COL = new ColumnKey(
            Bundle.AnalysisResultDAO_columnKeys_justification_name(),
            Bundle.AnalysisResultDAO_columnKeys_justification_displayName(),
            Bundle.AnalysisResultDAO_columnKeys_justification_description()
    );

    static final ColumnKey SOURCE_TYPE_COL = new ColumnKey(
            Bundle.AnalysisResultDAO_columnKeys_sourceType_name(),
            Bundle.AnalysisResultDAO_columnKeys_sourceType_displayName(),
            Bundle.AnalysisResultDAO_columnKeys_sourceType_description()
    );

    synchronized static AnalysisResultDAO getInstance() {
        if (instance == null) {
            instance = new AnalysisResultDAO();
        }
        return instance;
    }

    /**
     * @return The set of types that are not shown in the tree.
     */
    public static Set<BlackboardArtifact.Type> getIgnoredTreeTypes() {
        return BlackboardArtifactDAO.getIgnoredTreeTypes();
    }

    // TODO We can probably combine all the caches at some point
    private final Cache<SearchParams<BlackboardArtifactSearchParam>, AnalysisResultTableSearchResultsDTO> analysisResultCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    private final Cache<SearchParams<HashHitSearchParam>, AnalysisResultTableSearchResultsDTO> hashHitCache = CacheBuilder.newBuilder().maximumSize(1000).build();
    private final Cache<SearchParams<KeywordHitSearchParam>, AnalysisResultTableSearchResultsDTO> keywordHitCache = CacheBuilder.newBuilder().maximumSize(1000).build();

    private AnalysisResultTableSearchResultsDTO fetchAnalysisResultsForTable(SearchParams<BlackboardArtifactSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();
        BlackboardArtifact.Type artType = cacheKey.getParamData().getArtifactType();
        
        List<BlackboardArtifact> arts = new ArrayList<>();
        String pagedWhereClause = getWhereClause(cacheKey);
        arts.addAll(blackboard.getAnalysisResultsWhere(pagedWhereClause));
        blackboard.loadBlackboardAttributes(arts);
        
        // Get total number of results
        long totalResultsCount = getTotalResultsCount(cacheKey, arts.size());  
        
        TableData tableData = createTableData(artType, arts);
        return new AnalysisResultTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), totalResultsCount);
    }

    private AnalysisResultTableSearchResultsDTO fetchSetNameHitsForTable(SearchParams<? extends AnalysisResultSetSearchParam> cacheKey) throws NoCurrentCaseException, TskCoreException {

        SleuthkitCase skCase = getCase();
        Blackboard blackboard = skCase.getBlackboard();

        Long dataSourceId = cacheKey.getParamData().getDataSourceId();
        BlackboardArtifact.Type artType = cacheKey.getParamData().getArtifactType();

        // We currently can't make a query on the set name field because need to use a prepared statement
        String originalWhereClause = " artifacts.artifact_type_id = " + artType.getTypeID() + " ";
        if (dataSourceId != null) {
            originalWhereClause += " AND artifacts.data_source_obj_id = " + dataSourceId + " ";
        }
        
        List<BlackboardArtifact> allHashHits = new ArrayList<>();
        allHashHits.addAll(blackboard.getAnalysisResultsWhere(originalWhereClause));
        blackboard.loadBlackboardAttributes(allHashHits);
        
        // Filter for the selected set
        List<BlackboardArtifact> hashHits = new ArrayList<>();
        for (BlackboardArtifact art : allHashHits) {
            BlackboardAttribute setNameAttr = art.getAttribute(BlackboardAttribute.Type.TSK_SET_NAME);
            if ((setNameAttr != null) && cacheKey.getParamData().getSetName().equals(setNameAttr.getValueString())) {
                hashHits.add(art);
            }
        }

        List<BlackboardArtifact> pagedArtifacts = getPaged(hashHits, cacheKey);
        TableData tableData = createTableData(artType, pagedArtifacts);
        return new AnalysisResultTableSearchResultsDTO(artType, tableData.columnKeys, tableData.rows, cacheKey.getStartItem(), hashHits.size());
    }

    @Override
    void addAnalysisResultColumnKeys(List<ColumnKey> columnKeys) {
        // Make sure these are in the same order as in addAnalysisResultFields()
        columnKeys.add(SOURCE_TYPE_COL);
        columnKeys.add(SCORE_COL);
        columnKeys.add(CONCLUSION_COL);
        columnKeys.add(CONFIGURATION_COL);
        columnKeys.add(JUSTIFICATION_COL);
    }

    @Override
    void addAnalysisResultFields(BlackboardArtifact artifact, List<Object> cells) throws TskCoreException {
        if (!(artifact instanceof AnalysisResult)) {
            throw new IllegalArgumentException("Can not add fields for artifact with ID: " + artifact.getId() + " - artifact must be an analysis result");
        }

        // Make sure these are in the same order as in addAnalysisResultColumnKeys()
        AnalysisResult analysisResult = (AnalysisResult) artifact;
        cells.add(getSourceObjType(analysisResult.getParent()));
        cells.add(analysisResult.getScore().getSignificance().getDisplayName());
        cells.add(analysisResult.getConclusion());
        cells.add(analysisResult.getConfiguration());
        cells.add(analysisResult.getJustification());
    }

    /**
     * Returns a displayable type string for the given content object.
     *
     * If the content object is a artifact of a custom type then this method may
     * cause a DB call BlackboardArtifact.getType
     *
     * @param source The object to determine the type of.
     *
     * @return A string representing the content type.
     */
    private String getSourceObjType(Content source) throws TskCoreException {
        if (source instanceof BlackboardArtifact) {
            BlackboardArtifact srcArtifact = (BlackboardArtifact) source;
            return srcArtifact.getType().getDisplayName();
        } else if (source instanceof Volume) {
            return TskData.ObjectType.VOL.toString();
        } else if (source instanceof AbstractFile) {
            return TskData.ObjectType.ABSTRACTFILE.toString();
        } else if (source instanceof Image) {
            return TskData.ObjectType.IMG.toString();
        } else if (source instanceof VolumeSystem) {
            return TskData.ObjectType.VS.toString();
        } else if (source instanceof OsAccount) {
            return TskData.ObjectType.OS_ACCOUNT.toString();
        } else if (source instanceof HostAddress) {
            return TskData.ObjectType.HOST_ADDRESS.toString();
        } else if (source instanceof Pool) {
            return TskData.ObjectType.POOL.toString();
        }
        return "";
    }

    @Override
    RowDTO createRow(BlackboardArtifact artifact, Content srcContent, Content linkedFile, boolean isTimelineSupported, List<Object> cellValues, long id) throws IllegalArgumentException {
        if (!(artifact instanceof AnalysisResult)) {
            throw new IllegalArgumentException("Can not make row for artifact with ID: " + artifact.getId() + " - artifact must be an analysis result");
        }
        return new AnalysisResultRowDTO((AnalysisResult) artifact, srcContent, isTimelineSupported, cellValues, id);
    }

    public AnalysisResultTableSearchResultsDTO getAnalysisResultsForTable(AnalysisResultSearchParam artifactKey, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        BlackboardArtifact.Type artType = artifactKey.getArtifactType();

        if (artType == null || artType.getCategory() != BlackboardArtifact.Category.ANALYSIS_RESULT
                || (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0)) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Artifact type must be non-null and analysis result.  Data source id must be null or > 0.  "
                    + "Received artifact type: {0}; data source id: {1}", artType, artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<BlackboardArtifactSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        if (hardRefresh) {
            analysisResultCache.invalidate(searchParams);
        }

        return analysisResultCache.get(searchParams, () -> fetchAnalysisResultsForTable(searchParams));
    }

    public boolean isAnalysisResultsInvalidating(AnalysisResultSearchParam key, ModuleDataEvent eventData) {
        return key.getArtifactType().equals(eventData.getBlackboardArtifactType());
    }

    public AnalysisResultTableSearchResultsDTO getHashHitsForTable(HashHitSearchParam artifactKey, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Data source id must be null or > 0.  "
                    + "Received data source id: {0}", artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<HashHitSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        if (hardRefresh) {
            hashHitCache.invalidate(searchParams);
        }

        return hashHitCache.get(searchParams, () -> fetchSetNameHitsForTable(searchParams));
    }

    // TODO - JIRA-8117
    // This needs to use more than just the set name
    public AnalysisResultTableSearchResultsDTO getKeywordHitsForTable(KeywordHitSearchParam artifactKey, long startItem, Long maxCount, boolean hardRefresh) throws ExecutionException, IllegalArgumentException {
        if (artifactKey.getDataSourceId() != null && artifactKey.getDataSourceId() < 0) {
            throw new IllegalArgumentException(MessageFormat.format("Illegal data.  "
                    + "Data source id must be null or > 0.  "
                    + "Received data source id: {0}", artifactKey.getDataSourceId() == null ? "<null>" : artifactKey.getDataSourceId()));
        }

        SearchParams<KeywordHitSearchParam> searchParams = new SearchParams<>(artifactKey, startItem, maxCount);
        if (hardRefresh) {
            keywordHitCache.invalidate(searchParams);
        }

        return keywordHitCache.get(searchParams, () -> fetchSetNameHitsForTable(searchParams));
    }

    public void dropAnalysisResultCache() {
        analysisResultCache.invalidateAll();
    }

    public void dropHashHitCache() {
        hashHitCache.invalidateAll();
    }

    public void dropKeywordHitCache() {
        keywordHitCache.invalidateAll();
    }

    /**
     * Returns a search results dto containing rows of counts data.
     *
     * @param dataSourceId The data source object id for which the results
     *                     should be filtered or null if no data source
     *                     filtering.
     *
     * @return The results where rows are row of AnalysisResultSearchParam.
     *
     * @throws ExecutionException
     */
    public TreeResultsDTO<AnalysisResultSearchParam> getAnalysisResultCounts(Long dataSourceId) throws ExecutionException {
        try {
            // get row dto's sorted by display name
            Map<BlackboardArtifact.Type, Long> typeCounts = getCounts(BlackboardArtifact.Category.ANALYSIS_RESULT, dataSourceId);
            List<TreeResultsDTO.TreeItemDTO<AnalysisResultSearchParam>> treeItemRows = typeCounts.entrySet().stream()
                    .map(entry -> {
                        return new TreeResultsDTO.TreeItemDTO<>(
                                BlackboardArtifact.Category.ANALYSIS_RESULT.name(),
                                new AnalysisResultSearchParam(entry.getKey(), dataSourceId),
                                entry.getKey().getTypeID(),
                                entry.getKey().getDisplayName(),
                                entry.getValue());
                    })
                    .sorted(Comparator.comparing(countRow -> countRow.getDisplayName()))
                    .collect(Collectors.toList());

            // return results
            return new TreeResultsDTO<>(treeItemRows);

        } catch (NoCurrentCaseException | TskCoreException ex) {
            throw new ExecutionException("An error occurred while fetching analysis result counts.", ex);
        }
    }

// GVDTODO code to use in a future PR
//    /**
//     *
//     * @param type         The artifact type to filter on.
//     * @param setNameAttr  The blackboard attribute denoting the set name.
//     * @param dataSourceId The data source object id for which the results
//     *                     should be filtered or null if no data source
//     *                     filtering.
//     *
//     * @return A mapping of set names to their counts.
//     *
//     * @throws IllegalArgumentException
//     * @throws ExecutionException
//     */
//    Map<String, Long> getSetCountsMap(BlackboardArtifact.Type type, BlackboardAttribute.Type setNameAttr, Long dataSourceId) throws IllegalArgumentException, ExecutionException {
//        if (dataSourceId != null && dataSourceId <= 0) {
//            throw new IllegalArgumentException("Expected data source id to be > 0");
//        }
//
//        try {
//            // get artifact types and counts
//            SleuthkitCase skCase = getCase();
//            String query = " set_name, COUNT(*) AS count \n"
//                    + "FROM ( \n"
//                    + "  SELECT art.artifact_id, \n"
//                    + "  (SELECT value_text \n"
//                    + "    FROM blackboard_attributes attr \n"
//                    + "    WHERE attr.artifact_id = art.artifact_id AND attr.attribute_type_id = " + setNameAttr.getTypeID() + " LIMIT 1) AS set_name \n"
//                    + "	 FROM blackboard_artifacts art \n"
//                    + "	 WHERE  art.artifact_type_id = " + type.getTypeID() + " \n"
//                    + ((dataSourceId == null) ? "" : "  AND art.data_source_obj_id = " + dataSourceId + " \n")
//                    + ") \n"
//                    + "GROUP BY set_name";
//
//            Map<String, Long> setCounts = new HashMap<>();
//            skCase.getCaseDbAccessManager().select(query, (resultSet) -> {
//                try {
//                    while (resultSet.next()) {
//                        String setName = resultSet.getString("set_name");
//                        long count = resultSet.getLong("count");
//                        setCounts.put(setName, count);
//                    }
//                } catch (SQLException ex) {
//                    logger.log(Level.WARNING, "An error occurred while fetching set name counts.", ex);
//                }
//            });
//
//            return setCounts;
//        } catch (NoCurrentCaseException | TskCoreException ex) {
//            throw new ExecutionException("An error occurred while fetching set counts", ex);
//        }
//    }
//
//    /**
//     * Get counts for individual sets of the provided type to be used in the
//     * tree view.
//     *
//     * @param type         The blackboard artifact type.
//     * @param dataSourceId The data source object id for which the results
//     *                     should be filtered or null if no data source
//     *                     filtering.
//     * @param nullSetName  For artifacts with no set, this is the name to
//     *                     provide. If null, artifacts without a set name will
//     *                     be ignored.
//     * @param converter    Means of converting from data source id and set name
//     *                     to an AnalysisResultSetSearchParam
//     *
//     * @return The sets along with counts to display.
//     *
//     * @throws IllegalArgumentException
//     * @throws ExecutionException
//     */
//    private <T extends AnalysisResultSetSearchParam> TreeResultsDTO<T> getSetCounts(
//            BlackboardArtifact.Type type,
//            Long dataSourceId,
//            String nullSetName,
//            BiFunction<Long, String, T> converter) throws IllegalArgumentException, ExecutionException {
//
//        List<TreeItemDTO<T>> allSets
//                = getSetCountsMap(type, BlackboardAttribute.Type.TSK_SET_NAME, dataSourceId).entrySet().stream()
//                        .filter(entry -> nullSetName != null || entry.getKey() != null)
//                        .map(entry -> {
//                            return new TreeItemDTO<>(
//                                    type.getTypeName(),
//                                    converter.apply(dataSourceId, entry.getKey()),
//                                    entry.getKey(),
//                                    entry.getKey() == null ? nullSetName : entry.getKey(),
//                                    entry.getValue());
//                        })
//                        .sorted((a, b) -> a.getDisplayName().compareToIgnoreCase(b.getDisplayName()))
//                        .collect(Collectors.toList());
//
//        return new TreeResultsDTO<>(allSets);
//    }
//
//    public TreeResultsDTO<HashHitSearchParam> getHashHitSetCounts(Long dataSourceId) throws IllegalArgumentException, ExecutionException {
//        return getSetCounts(BlackboardArtifact.Type.TSK_HASHSET_HIT, dataSourceId, null, (dsId, setName) -> new HashHitSearchParam(dsId, setName));
//    }
//
//    public TreeResultsDTO<AnalysisResultSetSearchParam> getSetCounts(BlackboardArtifact.Type type, Long dataSourceId, String nullSetName) throws IllegalArgumentException, ExecutionException {
//        return getSetCounts(type, dataSourceId, nullSetName, (dsId, setName) -> new AnalysisResultSetSearchParam(type, dsId, setName));
//    }

    
    /**
     * Handles basic functionality of fetching and paging of analysis results.
     */
    static abstract class AbstractAnalysisResultFetcher<T extends AnalysisResultSearchParam> extends DAOFetcher<T> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public AbstractAnalysisResultFetcher(T params) {
            super(params);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            ModuleDataEvent dataEvent = this.getModuleDataFromEvt(evt);
            if (dataEvent == null) {
                return false;
            }

            return MainDAO.getInstance().getAnalysisResultDAO().isAnalysisResultsInvalidating(this.getParameters(), dataEvent);
        }
    }

    /**
     * Handles fetching and paging of analysis results.
     */
    public static class AnalysisResultFetcher extends AbstractAnalysisResultFetcher<AnalysisResultSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public AnalysisResultFetcher(AnalysisResultSearchParam params) {
            super(params);
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getAnalysisResultDAO().getAnalysisResultsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }
    }

    /**
     * Handles fetching and paging of hashset hits.
     */
    public static class HashsetResultFetcher extends AbstractAnalysisResultFetcher<HashHitSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public HashsetResultFetcher(HashHitSearchParam params) {
            super(params);
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getAnalysisResultDAO().getHashHitsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }
    }

    /**
     * Handles fetching and paging of keyword hits.
     */
    public static class KeywordHitResultFetcher extends AbstractAnalysisResultFetcher<KeywordHitSearchParam> {

        /**
         * Main constructor.
         *
         * @param params Parameters to handle fetching of data.
         */
        public KeywordHitResultFetcher(KeywordHitSearchParam params) {
            super(params);
        }

        @Override
        public SearchResultsDTO getSearchResults(int pageSize, int pageIdx, boolean hardRefresh) throws ExecutionException {
            return MainDAO.getInstance().getAnalysisResultDAO().getKeywordHitsForTable(this.getParameters(), pageIdx * pageSize, (long) pageSize, hardRefresh);
        }
    }
}