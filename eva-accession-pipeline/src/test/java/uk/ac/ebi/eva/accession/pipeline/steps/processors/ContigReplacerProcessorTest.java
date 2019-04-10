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
package uk.ac.ebi.eva.accession.pipeline.steps.processors;

import org.junit.Before;
import org.junit.Test;
import uk.ac.ebi.eva.accession.core.contig.ContigMapping;
import uk.ac.ebi.eva.commons.core.models.IVariant;
import uk.ac.ebi.eva.commons.core.models.pipeline.Variant;

import static org.junit.Assert.assertEquals;

public class ContigReplacerProcessorTest {

    private ContigReplacerProcessor processor;

    @Before
    public void setUp() throws Exception {
        String fileString = ContigReplacerProcessorTest.class.getResource(
                "/input-files/assembly-report/assembly_report.txt").toString();
        ContigMapping contigMapping = new ContigMapping(fileString);

        processor = new ContigReplacerProcessor(contigMapping);
    }

    @Test
    public void ContigGenbank() throws Exception {
        IVariant variant = new Variant("CM000093.4", 1, 1, "A", "T");
        assertEquals("CM000093.4", processor.process(variant).getChromosome());
    }

    @Test
    public void ContigChrToGenbank() throws Exception {
        IVariant variant = new Variant("chr2", 1, 1, "A", "T");
        assertEquals("CM000094.4", processor.process(variant).getChromosome());
    }

    @Test
    public void ContigRefseqToGenbank() throws Exception {
        IVariant variant = new Variant("NW_003763476.1", 1, 1, "A", "T");
        assertEquals("CM000093.4", processor.process(variant).getChromosome());
    }

    @Test
    public void ContigGenbankAndRefseqNotEquivalents() throws Exception {
        IVariant variant = new Variant("chr3", 1, 1, "A", "T");
        assertEquals("chr3", processor.process(variant).getChromosome());
    }

    @Test
    public void ContigNotFoundInAssemblyReport() throws Exception {
        IVariant variant = new Variant("chr", 1, 1, "A", "T");
        assertEquals("chr", processor.process(variant).getChromosome());
    }

    @Test
    public void NoGenbankDontConvert() throws Exception {
        IVariant variant = new Variant("chr4", 1, 1, "A", "T");
        assertEquals("chr4", processor.process(variant).getChromosome());
    }

    @Test
    public void RelationshipColumnDifferentDontConvert() throws Exception {
        IVariant variant = new Variant("chr5", 1, 1, "A", "T");
        assertEquals("chr5", processor.process(variant).getChromosome());
    }

}