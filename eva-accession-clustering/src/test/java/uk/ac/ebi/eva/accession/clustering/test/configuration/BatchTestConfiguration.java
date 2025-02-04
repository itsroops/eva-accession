/*
 *
 *  * Copyright 2020 EMBL - European Bioinformatics Institute
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package uk.ac.ebi.eva.accession.clustering.test.configuration;

import org.springframework.batch.core.Job;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.batch.BatchProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.springframework.transaction.PlatformTransactionManager;

import uk.ac.ebi.eva.accession.clustering.configuration.batch.io.TargetSSReaderForBackPropRSConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.io.BackPropagatedRSWriterConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.io.ClusteringMongoReaderConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.io.ClusteringWriterConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.io.RSMergeAndSplitCandidatesReaderConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.io.RSMergeAndSplitWriterConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.io.VcfReaderConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.jobs.BackPropagateRSJobConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.jobs.ClusterUnclusteredVariantsJobConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.jobs.ClusteringFromMongoJobConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.jobs.ClusteringFromVcfJobConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.jobs.ProcessRemappedVariantsWithRSJobConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.jobs.StudyClusteringJobConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.jobs.qc.NewClusteredVariantsQCJobConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.listeners.ListenersConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.policies.ChunkSizeCompletionPolicyConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.processors.ClusteringVariantProcessorConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.steps.ClusteringFromMongoStepConfiguration;
import uk.ac.ebi.eva.accession.clustering.configuration.batch.steps.ClusteringFromVcfStepConfiguration;
import uk.ac.ebi.eva.accession.clustering.runner.ClusteringCommandLineRunner;
import uk.ac.ebi.eva.commons.batch.job.JobExecutionApplicationListener;

import javax.sql.DataSource;

import static uk.ac.ebi.eva.accession.clustering.configuration.BeanNames.*;
import static uk.ac.ebi.eva.accession.clustering.configuration.batch.jobs.qc.NewClusteredVariantsQCJobConfiguration.NEW_CLUSTERED_VARIANTS_QC_JOB;

@EnableAutoConfiguration
@Import({ClusteringFromVcfJobConfiguration.class,
        ClusteringFromMongoJobConfiguration.class,
        StudyClusteringJobConfiguration.class,
        NewClusteredVariantsQCJobConfiguration.class,
        ProcessRemappedVariantsWithRSJobConfiguration.class,
        ClusterUnclusteredVariantsJobConfiguration.class,
        BackPropagateRSJobConfiguration.class,
        ClusteringFromVcfStepConfiguration.class,
        ClusteringFromMongoStepConfiguration.class,
        VcfReaderConfiguration.class,
        RSMergeAndSplitCandidatesReaderConfiguration.class,
        RSMergeAndSplitWriterConfiguration.class,
        ClusteringMongoReaderConfiguration.class,
        ClusteringVariantProcessorConfiguration.class,
        ClusteringWriterConfiguration.class,
        TargetSSReaderForBackPropRSConfiguration.class,
        BackPropagatedRSWriterConfiguration.class,
        ListenersConfiguration.class,
        ClusteringCommandLineRunner.class,
        ChunkSizeCompletionPolicyConfiguration.class})
public class BatchTestConfiguration {

    public static final String JOB_LAUNCHER_FROM_VCF = "JOB_LAUNCHER_FROM_VCF";

    public static final String JOB_LAUNCHER_FROM_MONGO = "JOB_LAUNCHER_FROM_MONGO";

    public static final String JOB_LAUNCHER_STUDY_FROM_MONGO = "JOB_LAUNCHER_STUDY_FROM_MONGO";

    public static final String JOB_LAUNCHER_NEW_CLUSTERED_VARIANTS_QC = "JOB_LAUNCHER_NEW_CLUSTERED_VARIANTS_QC";

    public static final String JOB_LAUNCHER_FROM_MONGO_ONLY_FIRST_STEP = "JOB_LAUNCHER_FROM_MONGO_ONLY_FIRST_STEP";

    @Autowired
    private BatchProperties properties;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    @Bean(JOB_LAUNCHER_FROM_VCF)
    public JobLauncherTestUtils jobLauncherTestUtils() {

        return new JobLauncherTestUtils() {
            @Override
            @Autowired
            public void setJob(@Qualifier(CLUSTERING_FROM_VCF_JOB) Job job) {
                super.setJob(job);
            }
        };
    }

    @Bean(JOB_LAUNCHER_FROM_MONGO)
    public JobLauncherTestUtils jobLauncherTestUtilsFromMongo() {

        return new JobLauncherTestUtils() {
            @Override
            @Autowired
            public void setJob(@Qualifier(CLUSTERING_FROM_MONGO_JOB) Job job) {
                super.setJob(job);
            }
        };
    }

    @Bean(JOB_LAUNCHER_STUDY_FROM_MONGO)
    public JobLauncherTestUtils jobLauncherTestUtilsStudyFromMongo() {

        return new JobLauncherTestUtils() {
            @Override
            @Autowired
            public void setJob(@Qualifier(STUDY_CLUSTERING_JOB) Job job) {
                super.setJob(job);
            }
        };
    }

    @Bean(JOB_LAUNCHER_NEW_CLUSTERED_VARIANTS_QC)
    public JobLauncherTestUtils jobLauncherTestUtilsNewClusteredVariants() {

        return new JobLauncherTestUtils() {
            @Override
            @Autowired
            public void setJob(@Qualifier(NEW_CLUSTERED_VARIANTS_QC_JOB) Job job) {
                super.setJob(job);
            }
        };
    }

    @Bean(JOB_LAUNCHER_FROM_MONGO_ONLY_FIRST_STEP)
    public JobLauncherTestUtils jobLauncherTestUtilsFromMongoOnlyFirstStep() {

        return new JobLauncherTestUtils() {
            @Override
            @Autowired
            public void setJob(@Qualifier(PROCESS_REMAPPED_VARIANTS_WITH_RS_JOB) Job job) {
                super.setJob(job);
            }
        };
    }

    @Bean
    public JobExecutionApplicationListener jobExecutionApplicationListener() {
        return new JobExecutionApplicationListener();
    }
}
