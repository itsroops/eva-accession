/*
 * Copyright 2019 EMBL - European Bioinformatics Institute
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.ebi.eva.accession.core.batch.io;

import com.lordofthejars.nosqlunit.annotation.UsingDataSet;
import com.lordofthejars.nosqlunit.mongodb.MongoDbConfigurationBuilder;
import com.lordofthejars.nosqlunit.mongodb.MongoDbRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import uk.ac.ebi.eva.accession.core.configuration.nonhuman.MongoConfiguration;
import uk.ac.ebi.eva.accession.core.model.dbsnp.DbsnpSubmittedVariantEntity;
import uk.ac.ebi.eva.accession.core.test.configuration.nonhuman.MongoTestConfiguration;
import uk.ac.ebi.eva.accession.core.test.rule.FixSpringMongoDbRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@RunWith(SpringRunner.class)
@TestPropertySource("classpath:ss-accession-test.properties")
@UsingDataSet(locations = {
        "/test-data/dbsnpSubmittedVariantEntity.json"
})
@ContextConfiguration(classes = {MongoConfiguration.class, MongoTestConfiguration.class})
public class MongoDbCursorItemReaderTest {

    private static final String TEST_DB = "submitted-variants-test";

    private static final String REFERENCE_ALLELE = "A";

    @Autowired
    private MongoTemplate mongoTemplate;

    //Required by nosql-unit
    @Autowired
    private ApplicationContext applicationContext;

    @Rule
    public MongoDbRule mongoDbRule = new FixSpringMongoDbRule(
            MongoDbConfigurationBuilder.mongoDb().databaseName(TEST_DB).build());

    private MongoDbCursorItemReader<DbsnpSubmittedVariantEntity> reader;

    @Before
    public void setUp() {
        reader = new MongoDbCursorItemReader<>();
        reader.setTemplate(mongoTemplate);
        reader.setTargetType(DbsnpSubmittedVariantEntity.class);
    }

    @After
    public void tearDown() {
        reader.close();
    }

    @Test
    public void basicStringQuery() throws Exception {
        reader.setQuery("{'ref': '" + REFERENCE_ALLELE + "'}");
        reader.open(new ExecutionContext());
        List<DbsnpSubmittedVariantEntity> variants = readIntoList();
        assertEquals(2, variants.size());

        for (DbsnpSubmittedVariantEntity variant : variants) {
            assertEquals(REFERENCE_ALLELE, variant.getReferenceAllele());
        }
    }

    @Test
    public void basicQuery() throws Exception {
        reader.setQuery(new Query(where("ref").is(REFERENCE_ALLELE)));
        reader.open(new ExecutionContext());
        List<DbsnpSubmittedVariantEntity> variants = readIntoList();
        assertEquals(2, variants.size());

        for (DbsnpSubmittedVariantEntity variant : variants) {
            assertEquals(REFERENCE_ALLELE, variant.getReferenceAllele());
        }
    }

    private List<DbsnpSubmittedVariantEntity> readIntoList() throws Exception {
        List<DbsnpSubmittedVariantEntity> variants = new ArrayList<>();
        DbsnpSubmittedVariantEntity variant;

        while ((variant = reader.read()) != null) {
            variants.add(variant);
        }

        return variants;
    }

}
