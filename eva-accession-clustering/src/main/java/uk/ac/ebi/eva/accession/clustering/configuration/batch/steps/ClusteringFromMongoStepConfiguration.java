/*
 * Copyright 2020 EMBL - European Bioinformatics Institute
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
package uk.ac.ebi.eva.accession.clustering.configuration.batch.steps;

import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.policy.SimpleCompletionPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.ac.ebi.eva.accession.core.model.eva.SubmittedVariantEntity;
import uk.ac.ebi.eva.accession.core.model.eva.SubmittedVariantOperationEntity;

import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.CLUSTERED_VARIANTS_MONGO_READER;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.CLUSTERING_CLUSTERED_VARIANTS_FROM_MONGO_STEP;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.CLUSTERING_NON_CLUSTERED_VARIANTS_FROM_MONGO_STEP;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.CLUSTERED_CLUSTERING_WRITER;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.NON_CLUSTERED_CLUSTERING_WRITER;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.NON_CLUSTERED_VARIANTS_MONGO_READER;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.PROCESS_RS_SPLIT_CANDIDATES_STEP;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.PROCESS_RS_MERGE_CANDIDATES_STEP;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.PROGRESS_LISTENER;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.RS_MERGE_CANDIDATES_READER;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.RS_MERGE_WRITER;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.RS_SPLIT_CANDIDATES_READER;
import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.RS_SPLIT_WRITER;

@Configuration
@EnableBatchProcessing
public class ClusteringFromMongoStepConfiguration {

    @Bean(CLUSTERING_CLUSTERED_VARIANTS_FROM_MONGO_STEP)
    public Step clusteringClusteredVariantStepMongoReader(
            @Qualifier(CLUSTERED_VARIANTS_MONGO_READER) ItemStreamReader<SubmittedVariantEntity> mongoReader,
            @Qualifier(CLUSTERED_CLUSTERING_WRITER) ItemWriter<SubmittedVariantEntity> submittedVariantWriter,
            @Qualifier(PROGRESS_LISTENER) StepExecutionListener progressListener,
            StepBuilderFactory stepBuilderFactory,
            SimpleCompletionPolicy chunkSizeCompletionPolicy) {
        TaskletStep step = stepBuilderFactory.get(CLUSTERING_CLUSTERED_VARIANTS_FROM_MONGO_STEP)
                .<SubmittedVariantEntity, SubmittedVariantEntity>chunk(chunkSizeCompletionPolicy)
                .reader(mongoReader)
                .writer(submittedVariantWriter)
                .listener(progressListener)
                .build();
        return step;
    }

    @Bean(PROCESS_RS_MERGE_CANDIDATES_STEP)
    public Step processRSMergeCandidatesStep(
            @Qualifier(RS_MERGE_CANDIDATES_READER)
                    ItemReader<SubmittedVariantOperationEntity> rsMergeCandidatesReader,
            @Qualifier(RS_MERGE_WRITER) ItemWriter<SubmittedVariantOperationEntity> rsMergeWriter,
            @Qualifier(PROGRESS_LISTENER) StepExecutionListener progressListener,
            StepBuilderFactory stepBuilderFactory,
            SimpleCompletionPolicy chunkSizeCompletionPolicy) {
        TaskletStep step = stepBuilderFactory.get(PROCESS_RS_MERGE_CANDIDATES_STEP)
                                             .<SubmittedVariantOperationEntity, SubmittedVariantOperationEntity>chunk(
                                                     chunkSizeCompletionPolicy)
                                             .reader(rsMergeCandidatesReader)
                                             .writer(rsMergeWriter)
                                             .listener(progressListener)
                                             .build();
        return step;
    }

    @Bean(PROCESS_RS_SPLIT_CANDIDATES_STEP)
    public Step processRSSplitCandidatesStep(
            @Qualifier(RS_SPLIT_CANDIDATES_READER)
                    ItemReader<SubmittedVariantOperationEntity> rsSplitCandidatesReader,
            @Qualifier(RS_SPLIT_WRITER) ItemWriter<SubmittedVariantOperationEntity> rsSplitWriter,
            @Qualifier(PROGRESS_LISTENER) StepExecutionListener progressListener,
            StepBuilderFactory stepBuilderFactory,
            SimpleCompletionPolicy chunkSizeCompletionPolicy) {
        TaskletStep step = stepBuilderFactory.get(PROCESS_RS_SPLIT_CANDIDATES_STEP)
                                             .<SubmittedVariantOperationEntity, SubmittedVariantOperationEntity>chunk(
                                                     chunkSizeCompletionPolicy)
                                             .reader(rsSplitCandidatesReader)
                                             .writer(rsSplitWriter)
                                             .listener(progressListener)
                                             .build();
        return step;
    }

    @Bean(CLUSTERING_NON_CLUSTERED_VARIANTS_FROM_MONGO_STEP)
    public Step clusteringNonClusteredVariantStepMongoReader(
            @Qualifier(NON_CLUSTERED_VARIANTS_MONGO_READER) ItemStreamReader<SubmittedVariantEntity> mongoReader,
            @Qualifier(NON_CLUSTERED_CLUSTERING_WRITER) ItemWriter<SubmittedVariantEntity> submittedVariantWriter,
            @Qualifier(PROGRESS_LISTENER) StepExecutionListener progressListener,
            StepBuilderFactory stepBuilderFactory,
            SimpleCompletionPolicy chunkSizeCompletionPolicy) {
        TaskletStep step = stepBuilderFactory.get(CLUSTERING_NON_CLUSTERED_VARIANTS_FROM_MONGO_STEP)
                .<SubmittedVariantEntity, SubmittedVariantEntity>chunk(chunkSizeCompletionPolicy)
                .reader(mongoReader)
                .writer(submittedVariantWriter)
                .listener(progressListener)
                .build();
        return step;
    }
}
