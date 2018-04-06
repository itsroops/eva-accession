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
package uk.ac.ebi.eva.accession.pipeline.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import uk.ac.ebi.eva.accession.core.SubmittedVariantAccessioningService;
import uk.ac.ebi.eva.accession.core.configuration.SubmittedVariantAccessioningConfiguration;
import uk.ac.ebi.eva.accession.pipeline.io.AccessionSummaryWriter;
import uk.ac.ebi.eva.accession.pipeline.io.AccessionWriter;
import uk.ac.ebi.eva.accession.pipeline.parameters.InputParameters;

import java.io.IOException;

import static uk.ac.ebi.eva.accession.pipeline.configuration.BeanNames.ACCESSION_WRITER;


/**
 * Configuration to inject a VcfReader as a Variant Reader bean.
 */
@Configuration
@Import({SubmittedVariantAccessioningConfiguration.class, InputParameters.class})
public class AccessionWriterConfiguration {

    @Bean(ACCESSION_WRITER)
    public AccessionWriter accessionWriter(SubmittedVariantAccessioningService service,
                                           AccessionSummaryWriter accessionSummaryWriter) throws IOException {
        return new AccessionWriter(service, accessionSummaryWriter);
    }

    @Bean
    AccessionSummaryWriter accessionSummaryWriter(InputParameters inputParameters) throws IOException {
        return new AccessionSummaryWriter(inputParameters.getFasta(), inputParameters.getOutputVcf());
    }


}
