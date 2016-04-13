/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.metron.enrichment.adapters.simplehbase;


import org.adrianwalker.multilinestring.Multiline;
import org.apache.metron.domain.SensorEnrichmentConfig;
import org.apache.metron.enrichment.bolt.CacheKey;
import org.apache.metron.hbase.converters.enrichment.EnrichmentKey;
import org.apache.metron.hbase.converters.enrichment.EnrichmentValue;
import org.apache.metron.hbase.lookup.EnrichmentLookup;
import org.apache.metron.integration.util.EnrichmentHelper;
import org.apache.metron.integration.util.mock.MockHTable;
import org.apache.metron.reference.lookup.LookupKV;
import org.apache.metron.reference.lookup.accesstracker.BloomAccessTracker;
import org.apache.metron.reference.lookup.accesstracker.PersistentAccessTracker;
import org.apache.metron.utils.JSONUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SimpleHBaseAdapterTest {
    private String cf = "cf";
    private String atTableName = "tracker";
    private final String hbaseTableName = "enrichments";
    private String sampleMessage = "{\"10.0.2.3.orientation\":\"north\"}";
    private EnrichmentLookup lookup;
    private static final String PLAYFUL_CLASSIFICATION_TYPE = "playful_classification";
    private static final Map<String, String> PLAYFUL_ENRICHMENT = new HashMap<String, String>() {{
        put("orientation", "north");
    }};

    /**
     {
     "index": "bro",
     "batchSize": 5,
     "enrichmentFieldMap": {
     "geo": ["ip_dst_addr", "ip_src_addr"],
     "host": ["host"]
     },
     "fieldToEnrichmentTypeMap": {
        "ip_dst_addr" : [ "10.0.2.3" ],
        "ip_src_addr" : [ "10.3.30.120" ]
        }
     }
     */
    @Multiline
    private static String sourceConfigStr;


    @Before
    public void setup() throws Exception {

        final MockHTable trackerTable = (MockHTable)MockHTable.Provider.addToCache(atTableName, cf);

        final MockHTable hbaseTable = (MockHTable)MockHTable.Provider.addToCache(hbaseTableName, cf);
        EnrichmentHelper.INSTANCE.load(hbaseTable, cf, new ArrayList<LookupKV<EnrichmentKey, EnrichmentValue>>(){{
            add(new LookupKV<>(new EnrichmentKey("10.0.2.3", "10.0.2.3")
                            , new EnrichmentValue(PLAYFUL_ENRICHMENT )
                    )
            );
        }});

        BloomAccessTracker bat = new BloomAccessTracker(hbaseTableName, 100, 0.03);
        PersistentAccessTracker pat = new PersistentAccessTracker(hbaseTableName, "0", trackerTable, cf, bat, 0L);
        lookup = new EnrichmentLookup(hbaseTable, cf, pat);

    }



    @Test
    public void testEnrich() throws Exception {
        SimpleHBaseAdapter sha = new SimpleHBaseAdapter();
        sha.lookup = lookup;
        SensorEnrichmentConfig broSc = JSONUtils.INSTANCE.load(sourceConfigStr, SensorEnrichmentConfig.class);
        JSONObject shaJson = sha.enrich(new CacheKey("ip_dst_addr", "10.0.2.3", broSc));


        Assert.assertNotNull(shaJson);

        JSONParser jsonParser = new JSONParser();
        JSONObject finalEnriched = (JSONObject) jsonParser.parse(sampleMessage);

        Assert.assertEquals(finalEnriched, shaJson);

    }



}