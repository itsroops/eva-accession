/*
 * Copyright 2018 EMBL - European Bioinformatics Institute
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
package uk.ac.ebi.eva.accession.dbsnp.configuration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import uk.ac.ebi.eva.accession.core.persistence.DbsnpClusteredVariantAccessioningRepository;
import uk.ac.ebi.eva.accession.core.persistence.DbsnpClusteredVariantEntity;
import uk.ac.ebi.eva.accession.core.persistence.DbsnpSubmittedVariantAccessioningRepository;
import uk.ac.ebi.eva.accession.core.persistence.DbsnpSubmittedVariantEntity;
import uk.ac.ebi.eva.accession.core.persistence.SubmittedVariantEntity;
import uk.ac.ebi.eva.accession.dbsnp.parameters.InputParameters;
import uk.ac.ebi.eva.accession.dbsnp.test.BatchTestConfiguration;
import uk.ac.ebi.eva.accession.dbsnp.test.TestConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.ac.ebi.eva.accession.dbsnp.configuration.BeanNames.IMPORT_DBSNP_VARIANTS_STEP;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {BatchTestConfiguration.class, TestConfiguration.class})
@TestPropertySource("classpath:application.properties")
public class ImportDbsnpVariantsStepConfigurationTest {

    private static final int EXPECTED_SUBMITTED_VARIANTS = 6;

    private static final int EXPECTED_CLUSTERED_VARIANTS = 4;

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private DbsnpSubmittedVariantAccessioningRepository submittedVariantRepository;

    @Autowired
    private DbsnpClusteredVariantAccessioningRepository clusteredVariantRepository;

    @Autowired
    private InputParameters inputParameters;

    @Before
    public void setUp() throws Exception {
        submittedVariantRepository.deleteAll();
        clusteredVariantRepository.deleteAll();
    }

    @Test
    @DirtiesContext
    public void executeStep() throws IOException {
        JobExecution jobExecution = jobLauncherTestUtils.launchStep(IMPORT_DBSNP_VARIANTS_STEP);
        assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());

        assertEquals(EXPECTED_SUBMITTED_VARIANTS, submittedVariantRepository.count());
        assertEquals(EXPECTED_CLUSTERED_VARIANTS, clusteredVariantRepository.count());

        List<DbsnpSubmittedVariantEntity> storedSubmittedVariants = new ArrayList<>();
        submittedVariantRepository.findAll().forEach(storedSubmittedVariants::add);

        Set<Long> variantsThatMatchAssembly = filterVariants(storedSubmittedVariants,
                                                             SubmittedVariantEntity::isAssemblyMatch);
        assertFilteredVariantsAre(variantsThatMatchAssembly, Arrays.asList(26201546L, 1540359250L, 25312602L));
        Set<Long> variantsSupportedByEvidencce = filterVariants(storedSubmittedVariants,
                                                                SubmittedVariantEntity::isSupportedByEvidence);
        assertFilteredVariantsAre(variantsSupportedByEvidencce,
                                  Arrays.asList(26201546L, 25062583L, 25312601L, 27587141L, 25312602L));
    }

    private Set<Long> filterVariants(List<DbsnpSubmittedVariantEntity> unfilteredVariants,
                                     Predicate<DbsnpSubmittedVariantEntity> predicate) {
        return unfilteredVariants.stream().filter(predicate).map(SubmittedVariantEntity::getAccession).collect(
                Collectors.toSet());
    }

    private void assertFilteredVariantsAre(Set<Long> variantsToCheck, List<Long> expectedVariants) {
        assertEquals(expectedVariants.size(), variantsToCheck.size());
        assertTrue(variantsToCheck.containsAll(expectedVariants));
    }
}