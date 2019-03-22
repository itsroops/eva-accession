/*
 *
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
 *
 */
package uk.ac.ebi.eva.accession.release.steps.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import uk.ac.ebi.eva.commons.core.models.IVariant;
import uk.ac.ebi.eva.commons.core.models.VariantType;
import uk.ac.ebi.eva.commons.core.models.pipeline.Variant;

/**
 * Transform a named variant {@link VariantType#SEQUENCE_ALTERATION} into a structural variant with symbolic alleles.
 *
 * Examples of structural variants taken from
 * <a href='https://samtools.github.io/hts-specs/VCFv4.3.pdf'>VCFv.3 spec</a> (section 5.3):
 * <pre>
 * {@code
 * #CHROM POS      ID   REF  ALT          QUAL FILTER INFO
 * 2        321682 .    T    <DEL>        6    PASS   SVTYPE=DEL;END=321887;SVLEN=-205
 * 2      14477084 .    C    <DEL:ME:ALU> 12   PASS   SVTYPE=DEL;END=14477381;SVLEN=-297
 * 3       9425916 .    C    <INS:ME:L1>  23   PASS   SVTYPE=INS;END=9425916;SVLEN=6027
 * }
 * </pre>
 *
 * Note that unlike regular INDELS, variants with symbolic alleles have the context bases only in the REF column.
 *
 * Also, note that the deletions have the symbolic allele in the ALT column.
 */
public class NamedVariantProcessor implements ItemProcessor<Variant, IVariant> {

    private static Logger logger = LoggerFactory.getLogger(NamedVariantProcessor.class);

    @Override
    public IVariant process(Variant variant) throws Exception {

        String oldReference = variant.getReference();
        String oldAlternate = variant.getAlternate();

        String newReference = oldReference;
        String newAlternate = oldAlternate;

        if (!isNamedAllele(oldReference) && !isNamedAllele(oldAlternate)) {
            // normal case without special alleles: ok as it is
        } else if (isNamedAllele(oldReference) && !isNamedAllele(oldAlternate)) {
            // swap the alleles, look this class' documentation
            newReference = oldAlternate;
            newAlternate = oldReference;
        } else if (!isNamedAllele(oldReference) && isNamedAllele(oldAlternate)) {
            // ALT named allele: ok as it is
        } else if (isNamedAllele(oldReference) && isNamedAllele(oldAlternate)) {
            throw new IllegalArgumentException(
                    "This variant (with named alleles in both the reference and alternate alleles) can't be written "
                    + "in VCF, as only the ALT column can have symbolic alleles: " + variant);
        } else {
            throw new IllegalStateException(
                    "This case was missed in the design of this class. There is a bug with this kind of variants: "
                    + variant);
        }

        Variant newVariant = new Variant(variant.getChromosome(), variant.getStart(), variant.getEnd(),
                                         convertNamedAlleleToSymbolicAllele(newReference),
                                         convertNamedAlleleToSymbolicAllele(newAlternate));

        newVariant.addSourceEntries(variant.getSourceEntries());
        return newVariant;
    }

    /**
     * Named variants have alleles surrounded by parentheses. Those parentheses will be changed for angular brackets
     * and white spaces will be replaced by underscore so they can be represented in VCF format as symbolic alleles.
     */
    private String convertNamedAlleleToSymbolicAllele(String allele) {
        if (isNamedAllele(allele)) {
            return ("<" + removeFirstAndLastCharacters(allele) + ">").replace(" ", "_");
        } else {
            return allele;
        }
    }

    private boolean isNamedAllele(String allele) {
        return allele.startsWith("(") && allele.endsWith(")");
    }

    private String removeFirstAndLastCharacters(String allele) {
        return allele.substring(1, allele.length() - 1);
    }
}
