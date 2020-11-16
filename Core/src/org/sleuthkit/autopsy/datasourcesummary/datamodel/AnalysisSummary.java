/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Providing data for the data source analysis tab.
 */
public class AnalysisSummary implements DefaultArtifactUpdateGovernor {

    private static final BlackboardAttribute.Type TYPE_SET_NAME = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_SET_NAME);

    private static final Set<String> EXCLUDED_KEYWORD_SEARCH_ITEMS = new HashSet<>(Arrays.asList(
            "PHONE NUMBERS",
            "IP ADDRESSES",
            "EMAIL ADDRESSES",
            "URLS",
            "CREDIT CARD NUMBERS"
    ));

    private static final Set<Integer> ARTIFACT_UPDATE_TYPE_IDS = new HashSet<>(Arrays.asList(
            ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT.getTypeID(),
            ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT.getTypeID(),
            ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID(),
            ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()
    ));

    private final SleuthkitCaseProvider provider;

    /**
     * Main constructor.
     */
    public AnalysisSummary() {
        this(SleuthkitCaseProvider.DEFAULT);
    }

    /**
     * Main constructor.
     *
     * @param provider The means of obtaining a sleuthkit case.
     */
    public AnalysisSummary(SleuthkitCaseProvider provider) {
        this.provider = provider;
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return ARTIFACT_UPDATE_TYPE_IDS;
    }

    /**
     * Gets counts for hashset hits.
     *
     * @param dataSource The datasource for which to identify hashset hits.
     *
     * @return The hashset set name with the number of hits in descending order.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<AnalysisCountRecord> getHashsetCounts(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        return getCountsData(dataSource, TYPE_SET_NAME, ARTIFACT_TYPE.TSK_HASHSET_HIT);
    }

    /**
     * Gets counts for keyword hits.
     *
     * @param dataSource The datasource for which to identify keyword hits.
     *
     * @return The keyword set name with the number of hits in descending order.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<AnalysisCountRecord> getKeywordCounts(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        return getCountsData(dataSource, TYPE_SET_NAME, ARTIFACT_TYPE.TSK_KEYWORD_HIT).stream()
                // make sure we have a valid set and that that set does not belong to the set of excluded items
                .filter((record) -> record != null && record.getIdentifier()!= null && !EXCLUDED_KEYWORD_SEARCH_ITEMS.contains(record.getIdentifier().toUpperCase().trim()))
                .collect(Collectors.toList());
    }

    /**
     * Gets counts for interesting item hits.
     *
     * @param dataSource The datasource for which to identify interesting item
     * hits.
     *
     * @return The interesting item set name with the number of hits in
     * descending order.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    public List<AnalysisCountRecord> getInterestingItemCounts(DataSource dataSource) throws SleuthkitCaseProviderException, TskCoreException {
        return getCountsData(dataSource, TYPE_SET_NAME, ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT, ARTIFACT_TYPE.TSK_INTERESTING_ARTIFACT_HIT);
    }

    /**
     * Get counts for the artifact of the specified type.
     *
     * @param dataSource The datasource.
     * @param keyType The attribute to use as the key type.
     * @param artifactTypes The types of artifacts for which to query.
     *
     * @return A list of AnalysisCountRecord. This list is sorted by the count
     * descending max to min.
     *
     * @throws SleuthkitCaseProviderException
     * @throws TskCoreException
     */
    private List<AnalysisCountRecord> getCountsData(DataSource dataSource, BlackboardAttribute.Type keyType, ARTIFACT_TYPE... artifactTypes)
            throws SleuthkitCaseProviderException, TskCoreException {

        if (dataSource == null) {
            return Collections.emptyList();
        }

        List<BlackboardArtifact> artifacts = new ArrayList<>();
        SleuthkitCase skCase = provider.get();

        // get all artifacts in one list for each artifact type
        for (ARTIFACT_TYPE type : artifactTypes) {
            artifacts.addAll(skCase.getBlackboard().getArtifacts(type.getTypeID(), dataSource.getId()));
        }

        // group those based on the value of the attribute type that should serve as a key
        Map<String, AnalysisCountRecord> countedKeys = artifacts.stream()
                .map((art) -> {
                    String key = DataSourceInfoUtilities.getStringOrNull(art, keyType);
                    return (StringUtils.isBlank(key)) ? null : Pair.of(key, art);
                })
                .filter((key) -> key != null)
                .collect(Collectors.toMap(
                        (r) -> r.getLeft(), 
                        (r) -> new AnalysisCountRecord(r.getLeft(), 1, r.getRight()), 
                        (r1, r2) -> new AnalysisCountRecord(r1.getIdentifier(), r1.getCount() + r2.getCount(), r1.getArtifact())));

        // sort from max to min counts
        return countedKeys.values().stream()
                .sorted((a, b) -> -Long.compare(a.getCount(), b.getCount()))
                .collect(Collectors.toList());
    }

    /**
     * A record for an analysis item and its count.
     */
    public static class AnalysisCountRecord {

        private final String identifier;
        private final long count;
        private final BlackboardArtifact artifact;

        /**
         * Main constructor.
         *
         * @param identifier The identifier.
         * @param count The count for how many times found.
         * @param artifact The artifact.
         */
        AnalysisCountRecord(String identifier, long count, BlackboardArtifact artifact) {
            this.identifier = identifier;
            this.count = count;
            this.artifact = artifact;
        }

        /**
         * @return The identifier for this analysis record.
         */
        public String getIdentifier() {
            return identifier;
        }

        /**
         * @return How many times found.
         */
        public long getCount() {
            return count;
        }

        /**
         * @return The relevant artifact.
         */
        public BlackboardArtifact getArtifact() {
            return artifact;
        }
    }
}
