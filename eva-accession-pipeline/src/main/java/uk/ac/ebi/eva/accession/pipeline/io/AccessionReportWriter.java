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
package uk.ac.ebi.eva.accession.pipeline.io;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import uk.ac.ebi.ampt2d.commons.accession.core.models.AccessionWrapper;

import uk.ac.ebi.eva.accession.core.ISubmittedVariant;
import uk.ac.ebi.eva.accession.core.SubmittedVariant;
import uk.ac.ebi.eva.accession.core.contig.ContigMapping;
import uk.ac.ebi.eva.accession.core.contig.ContigNaming;
import uk.ac.ebi.eva.accession.core.contig.ContigSynonyms;
import uk.ac.ebi.eva.accession.core.io.FastaSequenceReader;
import uk.ac.ebi.eva.accession.pipeline.steps.processors.ContigToGenbankReplacerProcessor;
import uk.ac.ebi.eva.accession.pipeline.steps.tasklets.reportCheck.AccessionWrapperComparator;
import uk.ac.ebi.eva.commons.core.models.IVariant;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AccessionReportWriter {

    private static final String SUBSNP_ACCESSION_PREFIX = "ss";

    private static final String VCF_MISSING_VALUE = ".";

    private static final String IS_HEADER_WRITTEN_KEY = "AccessionReportWriter_isHeaderWritten";

    private static final String IS_HEADER_WRITTEN_VALUE = "true";   // use string because ExecutionContext doesn't support boolean

    private static final Logger logger = LoggerFactory.getLogger(AccessionReportWriter.class);

    private static final String ORIGINAL_CHROMOSOME = "CHR";

    private final File output;

    private final File temporaryOutput;

    private ContigMapping contigMapping;

    private FastaSequenceReader fastaSequenceReader;

    private BufferedWriter outputWriter;

    private BufferedWriter temporaryOutputWriter;

    private String accessionPrefix;

    private ContigNaming contigNaming;

    private Set<String> loggedUnreplaceableContigs;

    private Map<String, String> inputContigsToInsdc;

    private Map<String, String> insdcToOutputContigs;

    private Map<String, Set<String>> duplicatedInputContigsToInsdc;

    private Map<String, Set<String>> duplicatedInsdcToOutputContigs;

    public AccessionReportWriter(File output, FastaSequenceReader fastaSequenceReader, ContigMapping contigMapping,
                                 ContigNaming contigNaming) throws IOException {
        this.fastaSequenceReader = fastaSequenceReader;
        this.output = output;
        this.temporaryOutput = new File(output.getPath() + ".tmp");
        this.contigMapping = contigMapping;
        this.contigNaming = contigNaming;
        this.accessionPrefix = SUBSNP_ACCESSION_PREFIX;
        this.loggedUnreplaceableContigs = new HashSet<>();
        this.inputContigsToInsdc = new HashMap<>();
        this.insdcToOutputContigs = new HashMap<>();
        this.duplicatedInputContigsToInsdc = new HashMap<>();
        this.duplicatedInsdcToOutputContigs = new HashMap<>();
    }

    public String getAccessionPrefix() {
        return accessionPrefix;
    }

    public void setAccessionPrefix(String accessionPrefix) {
        this.accessionPrefix = accessionPrefix;
    }

    public void open(ExecutionContext executionContext) throws ItemStreamException {
        boolean isHeaderAlreadyWritten = IS_HEADER_WRITTEN_VALUE.equals(executionContext.get(IS_HEADER_WRITTEN_KEY));
        if ((output.exists() || temporaryOutput.exists()) && !isHeaderAlreadyWritten) {
            logger.warn("According to the job's execution context, the accession report should not exist, but it does" +
                                " exist. The AccessionReportWriter will append to the file, but it's possible that " +
                                "there will be 2 non-contiguous header sections in the report VCF. This can happen if" +
                                " the job execution context was not properly retrieved from the job repository.");
        }
        try {
            // TODO what to do if the job was interrupted and resumed?
            boolean append = true;
            this.outputWriter = new BufferedWriter(new FileWriter(this.output, append));
            this.temporaryOutputWriter = new BufferedWriter(new FileWriter(this.temporaryOutput, append));
            if (!isHeaderAlreadyWritten) {
                executionContext.put(IS_HEADER_WRITTEN_KEY, IS_HEADER_WRITTEN_VALUE);
            }
        } catch (IOException e) {
            throw new ItemStreamException(e);
        }
    }

    public void update(ExecutionContext executionContext) throws ItemStreamException {

    }

    public void close() throws ItemStreamException {
        try {
            temporaryOutputWriter.close();
            writeHeader(outputWriter);
            appendFileToWriter(temporaryOutput, outputWriter);
            outputWriter.close();
        } catch (IOException e) {
            throw new ItemStreamException(e);
        }
    }

    private void appendFileToWriter(File inputFile, BufferedWriter output) throws IOException {
        FileReader fileReader = new FileReader(inputFile);
        char[] buffer = new char[4000];
        int charactersRead = fileReader.read(buffer);
        boolean endOfFileReached = charactersRead == -1;

        while (!endOfFileReached) {
            output.write(buffer, 0, charactersRead);
            charactersRead = fileReader.read(buffer);
            endOfFileReached = charactersRead == -1;
        }
    }

    private void writeHeader(BufferedWriter writer) throws IOException {
        writer.write("##fileformat=VCFv4.2");
        writer.newLine();
        for(Map.Entry<String, String> contigAndChromosome : inputContigsToInsdc.entrySet()) {
            writer.write("##contig=<ID=" + contigAndChromosome.getKey()
                         + ",Description=\"" + contigAndChromosome.getValue() + "\">");
            writer.newLine();
        }
        writer.write("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO");
        writer.newLine();
    }

    public void write(List<? extends IVariant> originalVariants,
                      List<AccessionWrapper<ISubmittedVariant, String, Long>> accessionedVariants) throws IOException {
        if (temporaryOutputWriter == null) {
            throw new IOException("The file " + output + " was not opened properly. Hint: Check that the code " +
                                          "called " + this.getClass().getSimpleName() + "::open");
        }
        updateChromosomeMappings(originalVariants);
        List<? extends AccessionWrapper<ISubmittedVariant, String, Long>> denormalizedVariants = denormalizeVariants(
                accessionedVariants);
        denormalizedVariants.sort(AccessionWrapperComparator.fromIVariant(originalVariants, inputContigsToInsdc));
        for (AccessionWrapper<ISubmittedVariant, String, Long> variant : denormalizedVariants) {
            writeSortedVariant(variant, inputContigsToInsdc);
        }
        temporaryOutputWriter.flush();
    }

    private void updateChromosomeMappings(List<? extends IVariant> originalVariantsWithReplacedContigs) {
        for (IVariant variantWithContig : originalVariantsWithReplacedContigs) {
            String originalChromosome = getOriginalChromosome(variantWithContig);

            String previousContig = inputContigsToInsdc.put(originalChromosome, variantWithContig.getChromosome());
            if (previousContig != null) {
                Set<String> contigsAssociatedToTheSameChromosome = duplicatedInputContigsToInsdc.computeIfAbsent(
                        originalChromosome, key -> new HashSet<>());
                contigsAssociatedToTheSameChromosome.add(variantWithContig.getChromosome());
                contigsAssociatedToTheSameChromosome.add(previousContig);
            }

            String previousChromosome = insdcToOutputContigs.put(variantWithContig.getChromosome(), originalChromosome);
            if (previousChromosome != null) {
                Set<String> chromosomesAssociatedToTheSameContig = duplicatedInsdcToOutputContigs.computeIfAbsent(
                        variantWithContig.getChromosome(), key -> new HashSet<>());
                chromosomesAssociatedToTheSameContig.add(originalChromosome);
                chromosomesAssociatedToTheSameContig.add(previousChromosome);
            }
        }
    }

    private String getOriginalChromosome(IVariant variant) {
        Set<String> originalChromosomes = variant.getSourceEntries()
                                                 .stream()
                                                 .map(se -> se.getAttributes().get(
                                                         ContigToGenbankReplacerProcessor.ORIGINAL_CHROMOSOME))
                                                 .collect(Collectors.toSet());

        if (originalChromosomes.size() != 1) {
            throw new IllegalStateException(
                    "Can not provide the original chromosome of a variant because there are several ones in its "
                    + "attributes. Contig '"
                    + variant.getChromosome() + "' replaced all of [" + String.join(", ", originalChromosomes)
                    + "] contigs");
        }
        return originalChromosomes.iterator().next();
    }

    private List<? extends AccessionWrapper<ISubmittedVariant, String, Long>> denormalizeVariants(
            List<? extends AccessionWrapper<ISubmittedVariant, String, Long>> accessions) {
        List<AccessionWrapper<ISubmittedVariant, String, Long>> denormalizedAccessions = new ArrayList<>();
        for (AccessionWrapper<ISubmittedVariant, String, Long> accession : accessions) {
            denormalizedAccessions.add(new AccessionWrapper<>(accession.getAccession(), accession.getHash(),
                                                              denormalizeVariant(accession.getData())));
        }
        return denormalizedAccessions;
    }

    private ISubmittedVariant denormalizeVariant(ISubmittedVariant normalizedVariant) {
        if (normalizedVariant.getReferenceAllele().isEmpty() || normalizedVariant.getAlternateAllele().isEmpty()) {
            if (fastaSequenceReader.doesContigExist(normalizedVariant.getContig())) {
                return createVariantWithContextBase(normalizedVariant);
            } else {
                throw new IllegalArgumentException("Contig '" + normalizedVariant.getContig()
                                                   + "' does not appear in the FASTA file ");
            }
        } else {
            return normalizedVariant;
        }
    }

    private ISubmittedVariant createVariantWithContextBase(ISubmittedVariant normalizedVariant) {
        String oldReference = normalizedVariant.getReferenceAllele();
        String oldAlternate = normalizedVariant.getAlternateAllele();
        long oldStart = normalizedVariant.getStart();
        ImmutableTriple<Long, String, String> contextNucleotideInfo =
                fastaSequenceReader.getContextNucleotideAndNewStart(normalizedVariant.getContig(), oldStart,
                                                                    oldReference, oldAlternate);

        return new SubmittedVariant(normalizedVariant.getReferenceSequenceAccession(),
                                    normalizedVariant.getTaxonomyAccession(),
                                    normalizedVariant.getProjectAccession(),
                                    normalizedVariant.getContig(),
                                    contextNucleotideInfo.getLeft(),
                                    contextNucleotideInfo.getMiddle(),
                                    contextNucleotideInfo.getRight(),
                                    normalizedVariant.getClusteredVariantAccession(),
                                    normalizedVariant.isSupportedByEvidence(),
                                    normalizedVariant.isAssemblyMatch(),
                                    normalizedVariant.isAllelesMatch(),
                                    normalizedVariant.isValidated(),
                                    normalizedVariant.getCreatedDate());

    }

    /**
     * Replace the contig using the requested contig naming and write the variant to the output file.
     *
     * Note how this is done after the sorting (using {@link AccessionWrapperComparator} because the mappings we
     * passed to it are mappings from the input naming to INSDC, not from input naming to requested output naming.
     */
    private void writeSortedVariant(AccessionWrapper<ISubmittedVariant, String, Long> denormalizedVariant,
                                    Map<String, String> contigsToChromosomes) throws IOException {
        String originalChromosome = contigsToChromosomes.get(denormalizedVariant.getData().getContig());
        String contigFromRequestedContigNaming = getEquivalentContig(originalChromosome, contigNaming);

        String variantLine = String.join("\t",
                                         contigFromRequestedContigNaming,
                                         Long.toString(denormalizedVariant.getData().getStart()),
                                         accessionPrefix + denormalizedVariant.getAccession(),
                                         denormalizedVariant.getData().getReferenceAllele(),
                                         denormalizedVariant.getData().getAlternateAllele(),
                                         VCF_MISSING_VALUE, VCF_MISSING_VALUE, VCF_MISSING_VALUE);
        temporaryOutputWriter.write(variantLine);
        temporaryOutputWriter.newLine();
    }

    /**
     * Note that we can't use {@link ContigToGenbankReplacerProcessor} here because we allow other replacements than
     * GenBank, while that class is used to replace to GenBank only (for writing in Mongo and for comparing input and
     * report VCFs).
     */
    private String getEquivalentContig(String oldContig, ContigNaming contigNaming) {
        ContigSynonyms contigSynonyms = contigMapping.getContigSynonyms(oldContig);
        if (contigSynonyms == null) {
            if (!loggedUnreplaceableContigs.contains(oldContig)) {
                loggedUnreplaceableContigs.add(oldContig);
                logger.warn("Will not replace contig '" + oldContig
                            + "' (in the current variant or any subsequent one) as requested because there are no "
                            + "synonyms available. (Hint: Is the assembly report correct and complete?)");
            }
            return oldContig;
        }

        String contigReplacement = contigMapping.getContigSynonym(oldContig, contigSynonyms, contigNaming);
        if (contigReplacement == null) {
            if (!loggedUnreplaceableContigs.contains(oldContig)) {
                loggedUnreplaceableContigs.add(oldContig);
                logger.warn("Will not replace contig '" + oldContig
                            + "' (in the current variant or any subsequent one) as requested because there is no "
                            + contigNaming + " synonym for it.");
            }
            return oldContig;
        }

        boolean genbankReplacedWithRefseq = oldContig.equals(contigSynonyms.getGenBank())
                                            && contigReplacement.equals(contigSynonyms.getRefSeq());

        boolean refseqReplacedWithGenbank = oldContig.equals(contigSynonyms.getRefSeq())
                                            && contigReplacement.equals(contigSynonyms.getGenBank());

        if (!contigSynonyms.isIdenticalGenBankAndRefSeq() && (genbankReplacedWithRefseq || refseqReplacedWithGenbank)) {
            if (!loggedUnreplaceableContigs.contains(oldContig)) {
                loggedUnreplaceableContigs.add(oldContig);
                logger.warn(
                        "Will not replace contig '" + oldContig + "' with " + contigNaming + " '" + contigReplacement
                        + "' (in the current variant or any subsequent one) as requested because those contigs "
                        + "are not identical according to the assembly report provided.");
            }
            return oldContig;
        }

        return contigReplacement;
    }

}
