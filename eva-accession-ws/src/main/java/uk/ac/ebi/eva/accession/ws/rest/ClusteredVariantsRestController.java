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
package uk.ac.ebi.eva.accession.ws.rest;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.ebi.ampt2d.commons.accession.core.exceptions.AccessionDeprecatedException;
import uk.ac.ebi.ampt2d.commons.accession.core.exceptions.AccessionDoesNotExistException;
import uk.ac.ebi.ampt2d.commons.accession.core.exceptions.AccessionMergedException;
import uk.ac.ebi.ampt2d.commons.accession.core.models.AccessionWrapper;
import uk.ac.ebi.ampt2d.commons.accession.core.models.HistoryEvent;
import uk.ac.ebi.ampt2d.commons.accession.core.models.IEvent;
import uk.ac.ebi.ampt2d.commons.accession.rest.dto.AccessionResponseDTO;
import uk.ac.ebi.ampt2d.commons.accession.rest.dto.HistoryEventDTO;
import uk.ac.ebi.eva.accession.core.model.ClusteredVariant;
import uk.ac.ebi.eva.accession.core.model.IClusteredVariant;
import uk.ac.ebi.eva.accession.core.model.ISubmittedVariant;
import uk.ac.ebi.eva.accession.core.model.SubmittedVariant;
import uk.ac.ebi.eva.accession.core.service.human.dbsnp.HumanDbsnpClusteredVariantAccessioningService;
import uk.ac.ebi.eva.accession.core.service.nonhuman.ClusteredVariantAccessioningService;
import uk.ac.ebi.eva.accession.core.service.nonhuman.ClusteredVariantOperationService;
import uk.ac.ebi.eva.accession.core.service.nonhuman.SubmittedVariantAccessioningService;
import uk.ac.ebi.eva.accession.ws.dto.VariantHistory;
import uk.ac.ebi.eva.accession.ws.service.ClusteredVariantsBeaconService;
import uk.ac.ebi.eva.commons.beacon.models.BeaconAlleleResponse;
import uk.ac.ebi.eva.commons.core.models.VariantType;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "/v1/clustered-variants")
@Api(tags = {"Clustered variants"})
public class ClusteredVariantsRestController {

    private SubmittedVariantAccessioningService submittedVariantsService;

    private ClusteredVariantsBeaconService beaconService;

    private HumanDbsnpClusteredVariantAccessioningService humanService;

    private ClusteredVariantAccessioningService nonHumanActiveService;

    private ClusteredVariantOperationService clusteredVariantOperationService;

    public ClusteredVariantsRestController(
            SubmittedVariantAccessioningService submittedVariantsService,
            ClusteredVariantsBeaconService beaconService,
            @Qualifier("humanService") HumanDbsnpClusteredVariantAccessioningService humanService,
            @Qualifier("nonhumanActiveService") ClusteredVariantAccessioningService nonHumanActiveService,
            ClusteredVariantOperationService clusterdVariantOperationService
    ) {
        this.submittedVariantsService = submittedVariantsService;
        this.beaconService = beaconService;
        this.humanService = humanService;
        this.nonHumanActiveService = nonHumanActiveService;
        this.clusteredVariantOperationService = clusterdVariantOperationService;
    }

    /**
     * In case the RS is not active and was merged into several other active RSs, an exception AccessionMergedException
     * will be thrown and a redirection to an active RS will be done by {@link EvaControllerAdvice}. Although it is
     * not entirely correct, it was decided to return only one of those merges as redirection, doesn't matter which one.
     */
    @ApiOperation(value = "Find clustered variants (RS) by identifier", notes = "This endpoint returns the clustered "
            + "variants (RS) represented by the given identifier. For a description of the response, see "
            + "https://github.com/EBIvariation/eva-accession/wiki/Import-accessions-from-dbSNP#clustered-variant-refsnp-or-rs")
    @GetMapping(value = "/{identifier}", produces = "application/json")
    public ResponseEntity<List<AccessionResponseDTO<ClusteredVariant, IClusteredVariant, String, Long>>> get(
            @PathVariable @ApiParam(value = "Numerical identifier of a clustered variant, e.g.: 3000000000",
                    required = true) Long identifier)
            throws AccessionMergedException, AccessionDoesNotExistException {
        try {
            List<AccessionResponseDTO<ClusteredVariant, IClusteredVariant, String, Long>> clusteredVariants =
                    new ArrayList<>();
            clusteredVariants.addAll(getNonHumanClusteredVariants(identifier));
            clusteredVariants.addAll(humanService.getAllByAccession(identifier).stream().map(this::toDTO)
                    .collect(Collectors.toList()));

            if (clusteredVariants.isEmpty()) {
                throw new AccessionDoesNotExistException(identifier);
            }
            return ResponseEntity.ok(clusteredVariants);
        } catch (AccessionDeprecatedException e) {
            // not done with an exception handler because the only way to get the accession parameter would be parsing
            // the exception message
            return ResponseEntity.status(HttpStatus.GONE).body(getDeprecatedClusteredVariant(identifier));
        }
    }

    @ApiOperation(value = "Find clustered variant (RS) history", notes = "This endpoint returns the history of clustered "
            + "variants (RS) represented by the given identifier. ")
    @GetMapping(value = "/{identifier}/history", produces = "application/json")
    public ResponseEntity<VariantHistory<ClusteredVariant, IClusteredVariant, String, Long>> getVariantHistory(
            @PathVariable @ApiParam(value = "Numerical identifier of a clustered variant, e.g.: 3000000000",
                    required = true) Long identifier) throws AccessionDoesNotExistException {
        List<AccessionResponseDTO<ClusteredVariant, IClusteredVariant, String, Long>> allVariants =
                new ArrayList<>();
        try {
            allVariants.addAll(getNonHumanClusteredVariants(identifier));
            allVariants.addAll(humanService.getAllByAccession(identifier).stream().map(this::toDTO)
                    .collect(Collectors.toList()));
        } catch (AccessionDeprecatedException | AccessionMergedException e) {

        }

        List<HistoryEventDTO<Long, ClusteredVariant>> allOperations = clusteredVariantOperationService.getAllOperations(identifier)
                .stream().map(this::toHistoryEventDTO).collect(Collectors.toList());

        if (allVariants.isEmpty() && allOperations.isEmpty()) {
            throw new AccessionDoesNotExistException(identifier);
        }
        return ResponseEntity.ok(new VariantHistory<>(allVariants, allOperations));
    }

    public HistoryEventDTO<Long, ClusteredVariant> toHistoryEventDTO(IEvent<? extends IClusteredVariant, Long> operation) {
        HistoryEvent<IClusteredVariant, Long> historyEvent = new HistoryEvent<>(operation.getEventType(), operation.getAccession(),
                operation.getInactiveObjects().get(0).getVersion(), operation.getDestinationAccession(), operation.getCreatedDate(), operation.getInactiveObjects().get(0).getModel());
        return new HistoryEventDTO<>(historyEvent, ClusteredVariant::new);
    }

    private List<AccessionResponseDTO<ClusteredVariant, IClusteredVariant, String, Long>> getNonHumanClusteredVariants(
            Long identifier) throws AccessionDeprecatedException, AccessionMergedException {
        try {
            return nonHumanActiveService.getAllByAccession(identifier).stream().map(this::toDTO)
                    .collect(Collectors.toList());
        } catch (AccessionDoesNotExistException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Retrieve the information in the collection for inactive objects.
     * <p>
     * This method is necessary because the behaviour of BasicRestController is to return the HttpStatus.GONE with an
     * error message in the body. We want instead to return the HttpStatus.GONE with the variant in the body.
     */
    private List<AccessionResponseDTO<ClusteredVariant, IClusteredVariant, String, Long>> getDeprecatedClusteredVariant(
            Long identifier) {
        return Collections.singletonList(new AccessionResponseDTO<>(nonHumanActiveService.getLastInactive(identifier),
                ClusteredVariant::new));
    }

    @ApiOperation(value = "Find submitted variants (SS) by clustered variant identifier (RS)", notes = "Given a "
            + "clustered variant identifier (RS), this endpoint returns all the submitted variants (SS) linked to"
            + " the former. For a description of the response, see "
            + "https://github.com/EBIvariation/eva-accession/wiki/Import-accessions-from-dbSNP#submitted-variant-subsnp-or-ss")
    @GetMapping(value = "/{identifier}/submitted", produces = "application/json")
    public List<AccessionResponseDTO<SubmittedVariant, ISubmittedVariant, String, Long>> getSubmittedVariants(
            @PathVariable @ApiParam(value = "Numerical identifier of a clustered variant, e.g.: 869808637",
                    required = true) Long identifier)
            throws AccessionDoesNotExistException, AccessionDeprecatedException, AccessionMergedException {
        // trigger the checks. if the identifier was merged, the EvaControllerAdvice will redirect to the correct URL
        nonHumanActiveService.getAllByAccession(identifier);

        List<AccessionWrapper<ISubmittedVariant, String, Long>> submittedVariants =
                submittedVariantsService.getByClusteredVariantAccessionIn(Collections.singletonList(identifier));

        return submittedVariants.stream()
                .map(wrapper -> new AccessionResponseDTO<>(wrapper, SubmittedVariant::new))
                .collect(Collectors.toList());
    }

    @ApiOperation(value = "Find a clustered variant (RS) by the identifying fields", notes = "This endpoint returns "
            + "the clustered variant (RS) represented by a given identifier. For a description of the response, see "
            + "https://github.com/EBIvariation/eva-accession/wiki/Import-accessions-from-dbSNP#clustered-variant-refsnp"
            + "-or-rs")
    @GetMapping(produces = "application/json")
    public ResponseEntity<List<AccessionResponseDTO<ClusteredVariant, IClusteredVariant, String, Long>>> getByIdFields(
            @RequestParam(name = "assemblyId") @ApiParam(value = "assembly accesion in GCA format, e.g.: GCA_000002305.1")
                    String assembly,
            @RequestParam(name = "referenceName") @ApiParam(value = "chromosome genbank accession, e.g.: CM000392.2")
                    String chromosome,
            @RequestParam(name = "start") @ApiParam(value = "start position, e.g.: 66275332") long start,
            @RequestParam(name = "variantType") VariantType variantType) {
        List<AccessionResponseDTO<ClusteredVariant, IClusteredVariant, String, Long>> clusteredVariants =
                new ArrayList<>();

        List<AccessionWrapper<IClusteredVariant, String, Long>> nonHumanClusteredVariants =
                nonHumanActiveService.getByIdFields(assembly, chromosome, start, variantType);
        nonHumanClusteredVariants.stream().map(this::toDTO).forEach(clusteredVariants::add);

        List<AccessionWrapper<IClusteredVariant, String, Long>> humanClusteredVariants =
                humanService.getByIdFields(assembly, chromosome, start, variantType);
        humanClusteredVariants.stream().map(this::toDTO).forEach(clusteredVariants::add);

        return clusteredVariants.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(clusteredVariants);
    }

    private AccessionResponseDTO<ClusteredVariant, IClusteredVariant, String, Long> toDTO(
            AccessionWrapper<IClusteredVariant, String, Long> clusteredVariantWrapper) {
        return new AccessionResponseDTO<>(clusteredVariantWrapper, ClusteredVariant::new);
    }

    @ApiOperation(value = "Find if a clustered variant (RS) with the given identifying fields exists in our database",
            notes = "This endpoint returns true or false to indicate if the RS ID is present. Optionally return the " +
                    "RS ID.")
    @GetMapping(value = "/beacon/query", produces = "application/json")
    public BeaconAlleleResponse doesVariantExist(
            @RequestParam(name = "assemblyId") @ApiParam(value = "assembly accesion in GCA format, e.g.: GCA_000002305.1")
                    String assembly,
            @RequestParam(name = "referenceName") @ApiParam(value = "chromosome genbank accession, e.g.: CM000392.2")
                    String chromosome,
            @RequestParam(name = "start") @ApiParam(value = "start position, e.g.: 66275332") long start,
            @RequestParam(name = "variantType") VariantType variantType,
            @RequestParam(name = "includeDatasetReponses", required = false)
                    boolean includeDatasetReponses,
            HttpServletResponse response) {
        try {
            BeaconAlleleResponse beaconAlleleResponse = beaconService
                    .queryBeaconClusteredVariant(assembly, chromosome, start, variantType, includeDatasetReponses);
            return beaconAlleleResponse;
        } catch (Exception ex) {
            int responseStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            response.setStatus(responseStatus);
            return beaconService.getBeaconResponseObjectWithError(chromosome, start, assembly, variantType,
                    responseStatus,
                    "Unexpected Error: " + ex.getMessage());
        }
    }
}

