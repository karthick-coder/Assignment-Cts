package com.jbhunt.pricing.agreement.service;

import com.jbhunt.infrastructure.exception.JBHValidationException;
import com.jbhunt.pricing.agreement.constants.CustomerAgreementConstants;
import com.jbhunt.pricing.agreement.constants.PricingAgreementConstants;
import com.jbhunt.pricing.agreement.dto.*;
import com.jbhunt.pricing.agreement.elasticsearch.client.CustomerAgreementESClient;
import com.jbhunt.pricing.agreement.entity.*;
import com.jbhunt.pricing.agreement.es.client.CustomerAgreementHighLevelESClient;
import com.jbhunt.pricing.agreement.integration.PricingConfigurationService;
import com.jbhunt.pricing.agreement.mapper.*;
import com.jbhunt.pricing.agreement.model.EffectiveExpiryDate;
import com.jbhunt.pricing.agreement.model.Section;
import com.jbhunt.pricing.agreement.properties.PricingAgreementProperties;
import com.jbhunt.pricing.agreement.repository.*;
import com.jbhunt.pricing.configuration.entity.ConfigurationParameterDetail;
import com.jbhunt.pricing.configuration.entity.SectionType;
import com.jbhunt.pricing.linehaul.client.PricingLineHaulClient;
import com.jbhunt.pricing.linehaul.dto.LineHaulSectionDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.ProducerTemplate;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.javers.core.diff.ListCompareAlgorithm;
import org.javers.core.diff.changetype.PropertyChange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.jbhunt.pricing.agreement.constants.CustomerAgreementConstants.*;
import static com.jbhunt.pricing.agreement.constants.CustomerAgreementErrorConstants.*;
import static com.jbhunt.pricing.agreement.constants.PricingAgreementConstants.*;
import static com.jbhunt.pricing.agreement.integration.PricingConfigurationService.getGracePeriodConfiguration;


@Slf4j
@Service
@AllArgsConstructor
public class CustomerAgreementContractSectionService implements EffectiveExpiryDateService {

    private final ProducerTemplate producerTemplate;
    private final InvalidReasonTypeService invalidReasonTypeService;
    private final PricingAgreementProperties pricingAgreementProperties;
    private final CustomerAgreementContractRepository customerAgreementContractRepository;
    private final CustomerAgreementContractSectionMapper customerAgreementContractSectionMapper;
    private final CustomerAgreementContractSectionRepository
            customerAgreementContractSectionRepository;
    private final CustomerAgreementSectionAccountRepository customerAgreementSectionAccountRepository;
    private final PricingConfigurationService pricingConfigurationService;
    private final CustomerAgreementSectionAccountService customerAgreementSectionAccountService;
    private final CustomerAgreementContractSectionVersionRepository
            customerAgreementContractSectionVersionRepository;
    private final CustomerSectionCargoService customerSectionCargoService;
    private final CustomerAgreementContractSectionVersionMapper
            customerAgreementContractSectionVersionMapper;
    private final SectionTypeRepository sectionTypeRepository;
    private final AgreementElasticSearchDTOMapper agreementElasticSearchDTOMapper;
    private final SectionMapper sectionMapper;
    private final EffectiveExpiryDateMapper effectiveExpiryDateMapper;
    private final CustomerAgreementSectionService customerAgreementSectionService;
    private final CustomerAgreementHighLevelESClient customerAgreementHighLevelESClient;
    private final CustomerAgreementESClient customerAgreementESClient;
    private final CustomerCargoService customerCargoService;
    private final CustomerAgreementChildUpdateDTOMapper customerAgreementChildUpdateDTOMapper;
    private final PricingLineHaulClient pricingLineHaulClient;

    @Transactional
    public CustomerAgreementContractSectionViewDTO saveCustomerAgreementContractSection(
            Integer customerAgreementContractID,
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        CustomerAgreementContractSection customerAgreementContractSection;
        if (!customerAgreementContractSectionDTO.getIsCreateAgreementFlow()) {
            validateSection(customerAgreementContractSectionDTO);
            customerAgreementContractSection =
                    saveSection(customerAgreementContractID, customerAgreementContractSectionDTO);
        } else {
            validateCustomerAgreementContractSection(customerAgreementContractSectionDTO);
            CustomerAgreementContract customerAgreementContract =
                    customerAgreementContractRepository.findOne(customerAgreementContractID);
            customerAgreementContractSection =
                    customerAgreementContractSectionRepository.save(
                            prepareCustomerAgreementContractSection(
                                    customerAgreementContract, customerAgreementContractSectionDTO));
            syncSectionDetailsWithES(
                    customerAgreementContractSectionDTO, customerAgreementContractSection);
        }
        return prepareCustomerAgreementContractSectionResponse(
                customerAgreementContractSection, customerAgreementContractSectionDTO);
    }

    private void syncSectionDetailsWithES(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
            CustomerAgreementContractSection customerAgreementContractSection) {
        String existingUniqueDocID = null;
        boolean updateFlow = false;
        AgreementElasticSearchDTO agreementElasticSearchDTO =
                createAgreementElasticSearchDTOResponse(
                        customerAgreementContractSectionDTO,
                        customerAgreementContractSection,
                        existingUniqueDocID,
                        updateFlow);
        try {
            customerAgreementHighLevelESClient.createOrUpdateCustomerAgreement(agreementElasticSearchDTO);
        } catch (Exception e) {
            postCustomerAgreementContractSectionToTopic(
                    agreementElasticSearchDTO,
                    pricingAgreementProperties.getCreateCustomerAgreementRoute(),
                    null);
        }
    }

    private void syncSectionsDetailsWithES(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
            List<CustomerAgreementContractSection> customerAgreementContractSections,
            CustomerAgreementContractSection customerAgreementContractSection) {
        if (customerAgreementContractSections.size() > CustomerAgreementConstants.ZERO) {
            List<AgreementElasticSearchDTO> agreementElasticSearchDTOs = new ArrayList<>();
            customerAgreementContractSections.forEach(
                    section -> {
                        String existingUniqueDocID =
                                section
                                        .getCustomerAgreementContract()
                                        .getCustomerAgreement()
                                        .getCustomerAgreementID()
                                        .toString()
                                        .concat(
                                                section
                                                        .getCustomerAgreementContract()
                                                        .getCustomerAgreementContractID()
                                                        .toString())
                                        .concat(section.getCustomerAgreementContractSectionID().toString());
                        AgreementElasticSearchDTO agreementElasticSearchDTO =
                                createAgreementElasticSearchDTOResponse(
                                        customerAgreementContractSectionDTO,
                                        section,
                                        existingUniqueDocID,
                                        Boolean.FALSE);
                        agreementElasticSearchDTO.setUniqueDocID(existingUniqueDocID);
                        agreementElasticSearchDTOs.add(agreementElasticSearchDTO);
                    });
            agreementElasticSearchDTOs.add(
                    createAgreementElasticSearchDTOResponse(
                            customerAgreementContractSectionDTO,
                            customerAgreementContractSection,
                            null,
                            Boolean.FALSE));
            try {
                customerAgreementHighLevelESClient.bulkCreateOrUpdateCustomerAgreement(
                        agreementElasticSearchDTOs);
            } catch (Exception e) {
                postCustomerAgreementContractSectionsToTopic(
                        agreementElasticSearchDTOs,
                        pricingAgreementProperties.getCreateCustomerAgreementRoute(),
                        null);
            }
        } else {
            syncSectionDetailsWithES(
                    customerAgreementContractSectionDTO, customerAgreementContractSection);
        }
    }

    private CustomerAgreementContractSection saveSection(
            Integer customerAgreementContractID,
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        CustomerAgreementContract customerAgreementContract =
                customerAgreementContractRepository.findOne(customerAgreementContractID);
        List<CustomerAgreementContractSectionAccount> updatedCustomerAgreementSectionAccounts =
                new ArrayList<>();
        updatedCustomerAgreementSectionAccounts =
                prepareSections(
                        customerAgreementContractSectionDTO, updatedCustomerAgreementSectionAccounts);
        CustomerAgreementContractSection newlyCreatedCustomerAgreementContractSection =
                saveSections(
                        customerAgreementContractSectionDTO,
                        customerAgreementContract,
                        updatedCustomerAgreementSectionAccounts);
        sycnUpdatedSectionsToES(
                customerAgreementContractSectionDTO,
                updatedCustomerAgreementSectionAccounts,
                newlyCreatedCustomerAgreementContractSection);
        return newlyCreatedCustomerAgreementContractSection;
    }

    private void sycnUpdatedSectionsToES(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
            List<CustomerAgreementContractSectionAccount> updatedCustomerAgreementSectionAccounts,
            CustomerAgreementContractSection newlyCreatedCustomerAgreementContractSection) {
        List<CustomerAgreementContractSection> impactedSections = new ArrayList<>();
        if (!CollectionUtils.isEmpty(updatedCustomerAgreementSectionAccounts)) {
            impactedSections =
                    customerAgreementContractSectionRepository.findAll(
                            updatedCustomerAgreementSectionAccounts.stream()
                                    .map(CustomerAgreementContractSectionAccount::getCustomerAgreementContractSection)
                                    .map(CustomerAgreementContractSection::getCustomerAgreementContractSectionID)
                                    .distinct()
                                    .collect(Collectors.toList()));
        }
        syncSectionsDetailsWithES(
                customerAgreementContractSectionDTO,
                impactedSections,
                newlyCreatedCustomerAgreementContractSection);
    }

    private List<CustomerAgreementContractSectionAccount> prepareSections(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
            List<CustomerAgreementContractSectionAccount> updatedCustomerAgreementSectionAccounts) {
        if (customerAgreementContractSectionDTO.getCustomerAgreementContractSectionAccountDTOs()
                != null) {
            updatedCustomerAgreementSectionAccounts =
                    prepareCustomerSections(customerAgreementContractSectionDTO);
        }
        return updatedCustomerAgreementSectionAccounts;
    }

    private CustomerAgreementContractSection saveSections(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
            CustomerAgreementContract customerAgreementContract,
            List<CustomerAgreementContractSectionAccount> updatedCustomerAgreementSectionAccounts) {
        customerAgreementSectionAccountRepository.save(updatedCustomerAgreementSectionAccounts.stream().distinct()
                .collect(Collectors.toList()));
        CustomerAgreementContractSection customerAgreementContractSection =
                prepareCustomerAgreementContractSection(
                        customerAgreementContract, customerAgreementContractSectionDTO);
        return customerAgreementContractSectionRepository.save(getSectionWithDistinctCustomerSectionAccount(customerAgreementContractSection));
    }

    private CustomerAgreementContractSection getSectionWithDistinctCustomerSectionAccount(CustomerAgreementContractSection customerAgreementContractSection) {
        customerAgreementContractSection.setCustomerAgreementContractSectionAccounts(customerAgreementContractSection.getCustomerAgreementContractSectionAccounts().stream().distinct()
                .collect(Collectors.toList()));
        return customerAgreementContractSection;
    }

    private List<CustomerAgreementContractSectionAccount> prepareCustomerSections(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        List<CustomerAgreementContractSectionAccount> customerAgreementContractSectionAccounts =
                fetchSectionsWithBillTosAssigned(customerAgreementContractSectionDTO);
        return getModifiedSectionAccounts(
                customerAgreementContractSectionAccounts,
                customerAgreementContractSectionDTO.getCustomerAgreementContractSectionAccountDTOs());
    }

    private void validateSection(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        List<ConfigurationParameterDetail> configurationParameterDetails =
                pricingConfigurationService.getDateRelatedConfigurations();
        validateSectionDetails(customerAgreementContractSectionDTO, configurationParameterDetails);
        if (customerAgreementContractSectionDTO.getCustomerAgreementContractSectionAccountDTOs()
                != null) {
            validateSectionBillToCodeDetails(
                    customerAgreementContractSectionDTO, configurationParameterDetails);
        }
    }

    private void validateCustomerAgreementContractSection(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        validateSectionType(
                customerAgreementContractSectionDTO.getCustomerAgreementContractSectionTypeName());
        validateSectionName(
                customerAgreementContractSectionDTO.getCustomerAgreementContractSectionName(),
                customerAgreementContractSectionDTO
                        .getCustomerAgreementContractDTO()
                        .getCustomerAgreementID());
        validateSectionDate(customerAgreementContractSectionDTO);
    }

    private void validateSectionType(String customerAgreementContractSectionTypeName) {
        if (!isValidSectionType(customerAgreementContractSectionTypeName)) {
            throw new JBHValidationException(INVALID_SECTION_TYPE);
        }
    }

    private boolean isValidSectionType(String customerAgreementContractSectionTypeName) {
        return sectionTypeRepository
                .findAll()
                .stream()
                .anyMatch(
                        sectionType ->
                                sectionType
                                        .getSectionTypeName()
                                        .equalsIgnoreCase(customerAgreementContractSectionTypeName));
    }

    private void validateSectionName(
            String customerAgreementContractSectionName, Integer customerAgreementID) {
        if (customerAgreementContractSectionRepository
                .existsByCustomerAgreementContractSectionNameAndCustomerAgreementContractCustomerAgreementCustomerAgreementID(
                        customerAgreementContractSectionName, customerAgreementID)) {
            throw new JBHValidationException(SECTION_NAME_ALREADY_EXISTS);
        }
    }

    private void validateSectionDate(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        if (!isValidDate(
                customerAgreementContractSectionDTO.getCustomerAgreementContractDTO().getEffectiveDate(),
                customerAgreementContractSectionDTO.getCustomerAgreementContractDTO().getExpirationDate(),
                customerAgreementContractSectionDTO.getEffectiveDate(),
                customerAgreementContractSectionDTO.getExpirationDate())) {
            throw new JBHValidationException(INVALID_DATE_RANGE_FOR_SECTION);
        }
    }

    private CustomerAgreementContractSection prepareCustomerAgreementContractSection(
            CustomerAgreementContract customerAgreementContract,
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        CustomerAgreementContractSection customerAgreementContractSection =
                customerAgreementContractSectionMapper.createCustomerAgreementContractSection(
                        customerAgreementContract,
                        customerAgreementContractSectionDTO,
                        invalidReasonTypeService.getInvalidReasonTypeByName(
                                pricingAgreementProperties.getInvalidReasonTypes().get(ACTIVE)));
        customerAgreementContractSection
                .getCustomerAgreementContractSectionVersions()
                .forEach(
                        customerAgreementContractSectionVersion ->
                                customerAgreementContractSectionVersion.setCustomerAgreementContractSection(
                                        customerAgreementContractSection));
        customerAgreementContractSection
                .getCustomerAgreementContractSectionAccounts()
                .forEach(
                        customerAgreementContractSectionAccount ->
                                customerAgreementContractSectionAccount.setCustomerAgreementContractSection(
                                        customerAgreementContractSection));
        return customerAgreementContractSection;
    }

    private void postCustomerAgreementContractSectionToTopic(
            AgreementElasticSearchDTO agreementElasticSearchDTO,
            String routeName,
            String existingUniqueDocID) {
        agreementElasticSearchDTO.setUniqueDocID(existingUniqueDocID);
        producerTemplate.requestBody(routeName, agreementElasticSearchDTO);
    }

    private CustomerAgreementContractSectionViewDTO prepareCustomerAgreementContractSectionResponse(
            CustomerAgreementContractSection customerAgreementContractSection,
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        CustomerAgreementContractSectionVersion customerAgreementContractSectionVersion =
                customerAgreementContractSection.getCustomerAgreementContractSectionVersions().get(0);
        return customerAgreementContractSectionMapper.createCustomerAgreementContractSectionViewDTO(
                customerAgreementContractSection,
                customerAgreementContractSectionVersion,
                customerAgreementContractSectionDTO);
    }

    private AgreementElasticSearchDTO createAgreementElasticSearchDTOResponse(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
            CustomerAgreementContractSection customerAgreementContractSection,
            String existingUniqueDocID,
            boolean updateFlow) {
        AgreementElasticSearchDTO esDTO = null;
        List<CustomerAgreementContractSectionAccountDTO> accountDTOsFromAccountHierarchy =
                getAccountDTOsFromAccountHierarchy(
                        customerAgreementContractSection
                                .getCustomerAgreementContract()
                                .getCustomerAgreement()
                                .getCustomerAgreementID());
        Map<Integer, CustomerAgreementContractSectionAccountDTO> hierarchyMap = accountDTOsFromAccountHierarchy.stream().
                collect(Collectors.toMap(CustomerAgreementContractSectionAccountDTO::getBillingPartyID, Function.identity()));
        if (existingUniqueDocID != null) {
            try {
                customerAgreementESClient.deleteCustomerAgreement(existingUniqueDocID);
            } catch (Exception e) {
                log.error(ES_DOCUMENT_NOT_EXISTS);
            }

            esDTO =
                    agreementElasticSearchDTOMapper.createAgreementElasticSearchDTO(
                            customerAgreementContractSectionDTO, customerAgreementContractSection, updateFlow);
        } else {
            esDTO =
                    agreementElasticSearchDTOMapper.createAgreementElasticSearchDTO(
                            customerAgreementContractSectionDTO, customerAgreementContractSection, updateFlow);
        }
        setBillingPartyDetails(esDTO, hierarchyMap);

        return esDTO;
    }

    private void setBillingPartyDetails(AgreementElasticSearchDTO esDTO, Map<Integer, CustomerAgreementContractSectionAccountDTO> hierarchyMap) {
        if (Objects.nonNull(esDTO)) {
            esDTO.getAgreementSectionRangeElasticSearchDTOs().forEach(version -> {
                if (!CollectionUtils.isEmpty(version.getAgreementBillToCodeElasticSearchDTOs())) {
                    version.getAgreementBillToCodeElasticSearchDTOs().forEach(billto -> {
                        CustomerAgreementContractSectionAccountDTO sectionDTO = hierarchyMap.get(billto.getBillingPartyID());
                        if (sectionDTO != null) {
                            billto.setBillingPartyCode(sectionDTO.getBillingPartyCode());
                            billto.setBillingPartyName(sectionDTO.getBillingPartyName());
                            billto.setBillingPartyDisplayName((new StringBuilder()).append(sectionDTO.getBillingPartyName()).append(" (").append(sectionDTO.getBillingPartyCode()).append(")").toString());
                        }
                    });
                }
            });
        }
    }


    public List<CustomerAgreementContractSectionLookUpDTO> getAgreementSectionLookupDetails(
            Integer customerAgreementID,
            LocalDate effectiveDate,
            LocalDate expirationDate,
            String status) {
        if (isValidDateRange(effectiveDate, expirationDate)) {
            return customerAgreementContractSectionRepository.getAgreementSectionLookupDetails(
                    customerAgreementID,
                    effectiveDate,
                    expirationDate,
                    status.equalsIgnoreCase(INVALID_REASON_ACTIVE)
                            ? INVALID_INDICATOR_NO
                            : INVALID_INDICATOR_YES,
                    status.equalsIgnoreCase(INVALID_REASON_ACTIVE)
                            ? INVALID_REASON_ACTIVE
                            : INVALID_REASON_INACTIVE);
        } else {
            throw new JBHValidationException(EFFECTIVE_DATE_GREATER_THAN_EXPIRATION_DATE);
        }
    }

    @Transactional
    public CustomerAgreementContractSection updateCustomerAgreementContractSection(
            Integer customerAgreementID,
            Integer customerAgreementContractID,
            Integer customerAgreementContractSectionID,
            Integer versionID,
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {

        CustomerAgreementChildUpdateDTO customerAgreementChildUpdateDTO = new CustomerAgreementChildUpdateDTO();
        CustomerAgreementContractSection responseCustomerAgreementContractSection = null;
        CustomerAgreementContractSectionVersion newVersion = null;
        boolean nameChanged = false;
        boolean isDateChanged = false;
        boolean isExpDateChanged = false;
        CustomerAgreementContractSection existingCustomerAgreementContractSection = getCustomerAgreementContractSection(customerAgreementContractSectionID);
        CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion = getCustomerAgreementContractSectionVersion(versionID, existingCustomerAgreementContractSection);
        if (existingCustomerAgreementContractSectionVersion != null) {
            boolean isAgreementInDraftStatus = isAgreementInDraftStatus(existingCustomerAgreementContractSection);
            Section newSection = sectionMapper.getSection(customerAgreementContractSectionDTO);
            Section existingSection = sectionMapper.getSection(existingCustomerAgreementContractSectionVersion);
            List<String> changedProperties = getChangedProperties(newSection, existingSection);
            EffectiveExpiryDate effectiveExpiryDate = effectiveExpiryDateMapper.getEffectiveExpiryDate(customerAgreementContractSectionDTO);
            // validations starts
            validateSectionDetails(customerAgreementID, customerAgreementContractSectionDTO, existingCustomerAgreementContractSectionVersion, changedProperties, isAgreementInDraftStatus);
            nameChanged = newSection.isNameChanged(changedProperties);
            List<CustomerAgreementContractSectionAccountDTO>
                    removedCustomerAgreementContractSectionAccountDTOs = new ArrayList<>();
            List<CustomerAgreementContractSectionAccountDTO>
                    newlySelectedCustomerAgreementContractSectionAccountDTOs = new ArrayList<>();
            customerAgreementContractSectionDTO
                    .getCustomerAgreementContractSectionAccountDTOs()
                    .forEach(
                            customerAgreementContractSectionAccountDTO -> {
                                isBillToRemoved(
                                        removedCustomerAgreementContractSectionAccountDTOs,
                                        customerAgreementContractSectionAccountDTO);
                                checkNewlyAddedBillToCodes(
                                        newlySelectedCustomerAgreementContractSectionAccountDTOs,
                                        customerAgreementContractSectionAccountDTO,
                                        existingCustomerAgreementContractSection);
                            });
            validateBilltoDetails(existingCustomerAgreementContractSection, newSection, effectiveExpiryDate, newlySelectedCustomerAgreementContractSectionAccountDTOs);
            // validations ends
            InvalidReasonType invalidReasonType = getInvalidReasonType(ACTIVE);
            isDateChanged = isDateChanged(customerAgreementContractSectionDTO, existingCustomerAgreementContractSectionVersion);
            inactivateBilltos(
                    customerAgreementContractSectionDTO,
                    existingCustomerAgreementContractSectionVersion,
                    existingCustomerAgreementContractSection,
                    removedCustomerAgreementContractSectionAccountDTOs,
                    isAgreementInDraftStatus,
                    isDateChanged);
            isExpDateChanged = newSection.isExpiryDateChanged(customerAgreementContractSectionDTO.getExpirationDate(),
                    existingCustomerAgreementContractSectionVersion.getExpirationDate());
            newVersion =
                    createVersion(
                            customerAgreementContractSectionID,
                            customerAgreementContractSectionDTO,
                            existingCustomerAgreementContractSection,
                            isAgreementInDraftStatus,
                            existingCustomerAgreementContractSectionVersion,
                            newSection,
                            changedProperties,
                            isDateChanged,
                            customerAgreementChildUpdateDTO);
            List<CustomerAgreementContractSectionAccount>
                    newlySelectedCustomerAgreementContractSectionAccount =
                    prepareCustomerAgreementContractSectionAccounts(
                            newlySelectedCustomerAgreementContractSectionAccountDTOs,
                            existingCustomerAgreementContractSection,
                            invalidReasonType);
            updateVersionDetails(
                    customerAgreementContractID,
                    customerAgreementContractSectionDTO,
                    newVersion,
                    existingCustomerAgreementContractSection,
                    newSection,
                    changedProperties, nameChanged);
            if (isAgreementInDraftStatus) {
                updateCargoDateRanges(
                        customerAgreementContractSectionID, customerAgreementContractSectionDTO, isDateChanged);
            }

            responseCustomerAgreementContractSection =
                    saveCustomerAgreementContractSection(
                            existingCustomerAgreementContractSection,
                            changedProperties,
                            isDateChanged,
                            newlySelectedCustomerAgreementContractSectionAccount);
            updateSectionToES(
                    customerAgreementID,
                    customerAgreementContractSectionID,
                    customerAgreementContractSectionDTO,
                    responseCustomerAgreementContractSection,
                    existingCustomerAgreementContractSectionVersion,
                    true);
        } else {
            throw new JBHValidationException(RECORD_NOT_FOUND);
        }
        customerAgreementChildUpdateDTO =
                customerAgreementChildUpdateDTOMapper.getCustomerAgreementChildUpdateDTO(responseCustomerAgreementContractSection, customerAgreementContractSectionDTO,
                        customerAgreementID, isDateChanged, nameChanged, existingCustomerAgreementContractSection.getCustomerAgreementContractSectionName(),
                        isExpDateChanged, existingCustomerAgreementContractSectionVersion, customerAgreementChildUpdateDTO);
        updateCustomerAgreementContractSectionToTopic(customerAgreementChildUpdateDTO, pricingAgreementProperties.getEditSectionRoute());
        return responseCustomerAgreementContractSection;
    }

    private InvalidReasonType getInvalidReasonType(String reasonType) {
        return invalidReasonTypeService.getInvalidReasonTypeByName(
                pricingAgreementProperties.getInvalidReasonTypes().get(reasonType));
    }

    private void validateBilltoDetails(CustomerAgreementContractSection existingCustomerAgreementContractSection, Section newSection, EffectiveExpiryDate effectiveExpiryDate, List<CustomerAgreementContractSectionAccountDTO> newlySelectedCustomerAgreementContractSectionAccountDTOs) {
        newSection.validateBillToCodeDate(
                effectiveExpiryDate, newlySelectedCustomerAgreementContractSectionAccountDTOs);
        isValidBillingPartyIDs(
                newlySelectedCustomerAgreementContractSectionAccountDTOs,
                existingCustomerAgreementContractSection.getCustomerAgreementContractSectionID());
    }

    private boolean isAgreementInDraftStatus(CustomerAgreementContractSection existingCustomerAgreementContractSection) {
        return DRAFT.equalsIgnoreCase(
                existingCustomerAgreementContractSection
                        .getCustomerAgreementContract()
                        .getCustomerAgreement()
                        .getAgreementStatus()
                        .getAgreementStatusName());
    }

    private CustomerAgreementContractSectionVersion getCustomerAgreementContractSectionVersion(Integer versionID, CustomerAgreementContractSection existingCustomerAgreementContractSection) {
        return existingCustomerAgreementContractSection
                .getCustomerAgreementContractSectionVersions()
                .stream()
                .filter(
                        version -> version.getCustomerAgreementContractSectionVersionID().equals(versionID))
                .findFirst()
                .orElse(null);
    }


    private CustomerAgreementContractSection saveCustomerAgreementContractSection(
            CustomerAgreementContractSection existingCustomerAgreementContractSection,
            List<String> changedProperties,
            boolean isDateChanged,
            List<CustomerAgreementContractSectionAccount>
                    newlySelectedCustomerAgreementContractSectionAccount) {
        CustomerAgreementContractSection responseCustomerAgreementContractSection;
        if (!CollectionUtils.isEmpty(changedProperties)
                || isDateChanged
                || !CollectionUtils.isEmpty(newlySelectedCustomerAgreementContractSectionAccount)) {
            responseCustomerAgreementContractSection =
                    customerAgreementContractSectionRepository.save(existingCustomerAgreementContractSection);
        } else {
            responseCustomerAgreementContractSection = existingCustomerAgreementContractSection;
        }
        return responseCustomerAgreementContractSection;
    }

    public String getUserId() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getPrincipal)
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast)
                .map(UserDetails::getUsername)
                .orElse(null);
    }

    private CustomerAgreementContractSectionVersion createVersion(Integer customerAgreementContractSectionID, CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO, CustomerAgreementContractSection existingCustomerAgreementContractSection, boolean isAgreementInDraftStatus, CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion,
                                                                  Section newSection, List<String> changedProperties, boolean isDateChanged, CustomerAgreementChildUpdateDTO customerAgreementChildUpdateDTO) {
        CustomerAgreementContractSectionVersion newVersion = null;
        if (!isAgreementInDraftStatus) {
            validateEffectiveDate(customerAgreementContractSectionDTO, existingCustomerAgreementContractSectionVersion);
            newSection.isValidRecord(existingCustomerAgreementContractSectionVersion);
            boolean isGracePeriodEdit = isGracePeriodEdit(existingCustomerAgreementContractSectionVersion);
            validateGracePeriod(customerAgreementContractSectionDTO, existingCustomerAgreementContractSectionVersion, changedProperties, isGracePeriodEdit);
            if (!customerAgreementContractSectionDTO.getExpirationDate().isEqual(existingCustomerAgreementContractSectionVersion.getExpirationDate())) {
                checkChildEntities(customerAgreementContractSectionID, customerAgreementContractSectionDTO);
            }
            List<Integer> deletedVersionIDs = new ArrayList<>();
            customerAgreementChildUpdateDTO.setDeletedVersionIDs(deletedVersionIDs);
            if (!CollectionUtils.isEmpty(changedProperties)) {
                if (isDateChanged) {
                    newVersion = splitVersions(customerAgreementContractSectionDTO, existingCustomerAgreementContractSection, existingCustomerAgreementContractSectionVersion, changedProperties);
                } else {
                    deleteExistingVersion(existingCustomerAgreementContractSectionVersion, customerAgreementChildUpdateDTO);
                    newVersion = createNewVersion(existingCustomerAgreementContractSection);
                    existingCustomerAgreementContractSection.getCustomerAgreementContractSectionVersions().add(newVersion);
                }
            } else {
                newVersion = isDateChanged ? splitVersions(customerAgreementContractSectionDTO, existingCustomerAgreementContractSection, existingCustomerAgreementContractSectionVersion, changedProperties)
                        : existingCustomerAgreementContractSectionVersion;
            }
            newVersion.setEffectiveDate(customerAgreementContractSectionDTO.getEffectiveDate());
            newVersion.setExpirationDate(customerAgreementContractSectionDTO.getExpirationDate());
            applyDateRules(existingCustomerAgreementContractSection, customerAgreementContractSectionDTO,customerAgreementChildUpdateDTO);
            updateBilltoDatesToSection(customerAgreementContractSectionDTO, existingCustomerAgreementContractSection);
        } else {
            newVersion = existingCustomerAgreementContractSectionVersion;
        }
        return newVersion;
    }

    private void validateGracePeriod(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO, CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion, List<String> changedProperties, boolean isGracePeriodEdit) {
        if (!isGracePeriodEdit && !CollectionUtils.isEmpty(changedProperties)
                && customerAgreementContractSectionDTO.getEffectiveDate().isEqual(existingCustomerAgreementContractSectionVersion.getEffectiveDate())) {
            throw new JBHValidationException(BEYOND_GRACE_PERIOD_EXCEPTION);
        }
    }

    private void validateLatestRecord(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO, boolean isAgreementInDraftStatus, CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion) {
        if (!isAgreementInDraftStatus && existingCustomerAgreementContractSectionVersion != null && existingCustomerAgreementContractSectionVersion.getLastUpdateTimestamp().isAfter(customerAgreementContractSectionDTO.getLastUpdateTimestamp())) {
            throw new JBHValidationException(STALE_EXCEPTION);
        }
    }

    private CustomerAgreementContractSectionVersion splitVersions(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
                                                                  CustomerAgreementContractSection existingCustomerAgreementContractSection,
                                                                  CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion,
                                                                  List<String> changedProperties
    ) {
        CustomerAgreementContractSectionVersion newSpliitedVersion = null;
        if ((customerAgreementContractSectionDTO.getEffectiveDate().isAfter(existingCustomerAgreementContractSectionVersion.getEffectiveDate())
                && customerAgreementContractSectionDTO.getEffectiveDate().isBefore(existingCustomerAgreementContractSectionVersion.getExpirationDate()))
                && ((customerAgreementContractSectionDTO.getExpirationDate().isBefore(existingCustomerAgreementContractSectionVersion.getExpirationDate())
                || customerAgreementContractSectionDTO.getExpirationDate().isEqual(existingCustomerAgreementContractSectionVersion.getExpirationDate())
                || customerAgreementContractSectionDTO.getExpirationDate().isAfter(existingCustomerAgreementContractSectionVersion.getExpirationDate())
        ))) {
            existingCustomerAgreementContractSectionVersion.setExpirationDate(customerAgreementContractSectionDTO.getEffectiveDate().minusDays(1));
            newSpliitedVersion = createNewVersion(existingCustomerAgreementContractSection);
            existingCustomerAgreementContractSection.getCustomerAgreementContractSectionVersions().add(newSpliitedVersion);
        } else if (customerAgreementContractSectionDTO.getEffectiveDate().isAfter(existingCustomerAgreementContractSectionVersion.getExpirationDate())
                && customerAgreementContractSectionDTO.getExpirationDate().isAfter(existingCustomerAgreementContractSectionVersion.getExpirationDate())) {
            newSpliitedVersion = createNewVersion(existingCustomerAgreementContractSection);
            existingCustomerAgreementContractSection.getCustomerAgreementContractSectionVersions().add(newSpliitedVersion);
        } else {
            newSpliitedVersion = existingCustomerAgreementContractSectionVersion;
        }
        return newSpliitedVersion;
    }

    private void validateSectionDetails(Integer customerAgreementID, CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
                                        CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion, List<String> changedProperties, boolean isAgreementInDraftStatus) {
        validateLatestRecord(
                customerAgreementContractSectionDTO,
                isAgreementInDraftStatus,
                existingCustomerAgreementContractSectionVersion);
        validateName(customerAgreementID, customerAgreementContractSectionDTO, changedProperties,
                existingCustomerAgreementContractSectionVersion.getCustomerAgreementContractSection().getCustomerAgreementContractSectionID());
        if (changedProperties.contains("customerAgreementContractSectionTypeName")) {
            validateSectionType(customerAgreementContractSectionDTO.getCustomerAgreementContractSectionTypeName());
        }
        boolean isDateChanged = isDateChanged(customerAgreementContractSectionDTO, existingCustomerAgreementContractSectionVersion);
        if (isDateChanged) {
            validateDateRules(customerAgreementContractSectionDTO);
        }
    }

    private void updateSectionToES(Integer customerAgreementID, Integer customerAgreementContractSectionID,
                                   CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
                                   CustomerAgreementContractSection responseCustomerAgreementContractSection,
                                   CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion, boolean updateFlow) {
        String existingUniqueDocID =
                getExistingUniqueDocId(
                        customerAgreementID,
                        existingCustomerAgreementContractSectionVersion
                                .getCustomerAgreementContractSection()
                                .getCustomerAgreementContract()
                                .getCustomerAgreementContractID(),
                        customerAgreementContractSectionID,
                        customerAgreementContractSectionDTO
                );
        AgreementElasticSearchDTO agreementElasticSearchDTO =
                createAgreementElasticSearchDTOResponse(
                        customerAgreementContractSectionDTO,
                        responseCustomerAgreementContractSection,
                        existingUniqueDocID, updateFlow);
        try {
            customerAgreementHighLevelESClient.createOrUpdateCustomerAgreement(agreementElasticSearchDTO);
        } catch (Exception e) {
            postCustomerAgreementContractSectionToTopic(
                    agreementElasticSearchDTO,
                    pricingAgreementProperties.getUpdateCustomerAgreementContractSectionRoute(),
                    existingUniqueDocID);
        }
    }

    private void updateVersionDetails(Integer customerAgreementContractID, CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO, CustomerAgreementContractSectionVersion newVersion, CustomerAgreementContractSection existingCustomerAgreementContractSection, Section newSection, List<String> changedProperties, boolean nameChanged) {
        updateContract(existingCustomerAgreementContractSection, changedProperties, customerAgreementContractID);
        newVersion.setCurrencyCode(newSection.getCurrencyCode());
        newVersion
                .setEffectiveDate(customerAgreementContractSectionDTO.getEffectiveDate());
        newVersion
                .setExpirationDate(customerAgreementContractSectionDTO.getExpirationDate());
        newVersion.setCustomerAgreementContractSectionName(
                customerAgreementContractSectionDTO.getCustomerAgreementContractSectionName());
        setSectionType(customerAgreementContractSectionDTO, newVersion);
        if (nameChanged) {
            setSectionName(existingCustomerAgreementContractSection);
        }
    }

    private void inactivateBilltos(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
                                   CustomerAgreementContractSectionVersion newVersion, CustomerAgreementContractSection existingCustomerAgreementContractSection,
                                   List<CustomerAgreementContractSectionAccountDTO> removedCustomerAgreementContractSectionAccountDTOs, boolean isAgreementInDraftStatus
            , boolean isDateChanged) {
        if (isAgreementInDraftStatus) {
            deleteRemovedBillToCodes(removedCustomerAgreementContractSectionAccountDTOs);
            updateBillToWithSectionDate(customerAgreementContractSectionDTO, existingCustomerAgreementContractSection, isDateChanged);
        } else {
            disAssociateBillTos(existingCustomerAgreementContractSection, removedCustomerAgreementContractSectionAccountDTOs,
                    customerAgreementContractSectionDTO);
        }

    }

    private void updateBillToWithSectionDate(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
                                             CustomerAgreementContractSection existingCustomerAgreementContractSection, boolean isDateChanged) {

        if (isDateChanged) {
            List<Integer> existingAccountIDs = customerAgreementContractSectionDTO.getCustomerAgreementContractSectionAccountDTOs()
                    .stream().filter(existingAccount -> existingAccount.getIsRemoved() != null && !existingAccount.getIsRemoved()
                            && existingAccount.getCustomerAgreementContractSectionAccountID() != null)
                    .map(CustomerAgreementContractSectionAccountDTO::getCustomerAgreementContractSectionAccountID).collect(Collectors.toList());
            List<CustomerAgreementContractSectionAccount> existingAccounts = existingCustomerAgreementContractSection.getCustomerAgreementContractSectionAccounts().stream()
                    .filter(existingAccount -> "N".equalsIgnoreCase(existingAccount.getInvalidIndicator())
                            && existingAccountIDs.contains(existingAccount.getCustomerAgreementContractSectionAccountID())).collect(Collectors.toList());
            existingAccounts.forEach(account -> {
                account.setEffectiveDate(customerAgreementContractSectionDTO.getEffectiveDate());
                account.setExpirationDate(customerAgreementContractSectionDTO.getExpirationDate());
            });
        }
    }

    private void updateBilltoDatesToSection(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
                                            CustomerAgreementContractSection existingCustomerAgreementContractSection) {
        List<Integer> existingAccountIDs = customerAgreementContractSectionDTO.getCustomerAgreementContractSectionAccountDTOs()
                .stream().filter(existingAccount -> existingAccount.getIsRemoved() != null && !existingAccount.getIsRemoved()
                        && existingAccount.getCustomerAgreementContractSectionAccountID() != null)
                .map(CustomerAgreementContractSectionAccountDTO::getCustomerAgreementContractSectionAccountID).collect(Collectors.toList());
        List<CustomerAgreementContractSectionAccount> existingAccounts = existingCustomerAgreementContractSection.getCustomerAgreementContractSectionAccounts().stream()
                .filter(existingAccount -> "N".equalsIgnoreCase(existingAccount.getInvalidIndicator())
                        && existingAccountIDs.contains(existingAccount.getCustomerAgreementContractSectionAccountID())).collect(Collectors.toList());
        List<CustomerAgreementContractSectionVersion> versions = existingCustomerAgreementContractSection.getCustomerAgreementContractSectionVersions().stream()
                .filter(version -> version.getEffectiveDate().isAfter(customerAgreementContractSectionDTO.getExpirationDate())).collect(Collectors.toList());
        existingAccounts.forEach(account -> {
                    if (CollectionUtils.isEmpty(versions) && account.getExpirationDate().isAfter(customerAgreementContractSectionDTO.getExpirationDate())) {
                        account.setExpirationDate(customerAgreementContractSectionDTO.getExpirationDate());
                    }
                }

        );
    }

    private CustomerAgreementContractSectionVersion createNewVersion(CustomerAgreementContractSection existingCustomerAgreementContractSection) {
        InvalidReasonType invalidReasonType = invalidReasonTypeService.getInvalidReasonTypeByName(
                pricingAgreementProperties.getInvalidReasonTypes().get(PricingAgreementConstants.ACTIVE));
        CustomerAgreementContractSectionVersion newVersion = new CustomerAgreementContractSectionVersion();
        newVersion.setInvalidIndicator("N");
        newVersion.setInvalidReasonType(invalidReasonType);
        newVersion.setCustomerAgreementContractSection(existingCustomerAgreementContractSection);
        return newVersion;
    }

    private void deleteExistingVersion(CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion, CustomerAgreementChildUpdateDTO customerAgreementChildUpdateDTO) {
        InvalidReasonType deletedReasonType =
                invalidReasonTypeService.getInvalidReasonTypeByName(
                        pricingAgreementProperties.getInvalidReasonTypes().get(PricingAgreementConstants.DELETED));
        existingCustomerAgreementContractSectionVersion.setInvalidIndicator("Y");
        existingCustomerAgreementContractSectionVersion.setInvalidReasonType(deletedReasonType);
        customerAgreementChildUpdateDTO.getDeletedVersionIDs().add(existingCustomerAgreementContractSectionVersion.getCustomerAgreementContractSectionVersionID());
    }

    private void checkChildEntities(Integer customerAgreementContractSectionID, CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        SectionChildEntitiesDTO childEntitiesDTO = customerAgreementSectionService.fetchPricingEntityCounts(customerAgreementContractSectionID,
                customerAgreementContractSectionDTO.getExpirationDate());
        if (childEntitiesDTO.getAccessorials() > 0 || childEntitiesDTO.getFuels() > 0
                || childEntitiesDTO.getMileages() > 0 || childEntitiesDTO.getRatingRules() > 0) {
            throw new JBHValidationException(CHILD_ENTITY_EXISTS);
        }
    }

    private void validateEffectiveDate(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO, CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion) {
        if (!customerAgreementContractSectionDTO.getEffectiveDate().isEqual(existingCustomerAgreementContractSectionVersion.getEffectiveDate())
                && customerAgreementContractSectionDTO.getEffectiveDate().isBefore(existingCustomerAgreementContractSectionVersion.getEffectiveDate())) {
            throw new JBHValidationException(EFFECTIVE_DATE_PRIOR_CURRENT_EFFECTIVE_DATE);
        }
    }

    private void disAssociateBillTos(CustomerAgreementContractSection existingCustomerAgreementContractSection,
                                     List<CustomerAgreementContractSectionAccountDTO> removedCustomerAgreementContractSectionAccountDTOs,
                                     CustomerAgreementContractSectionDTO sectionDTO) {
        InvalidReasonType invalidReasonType = new InvalidReasonType();
        invalidReasonType.setInvalidReasonTypeID(3);
        invalidReasonType.setInvalidReasonTypeName("Deleted");
        List<CustomerAgreementContractSectionVersion> versions = existingCustomerAgreementContractSection.getCustomerAgreementContractSectionVersions().stream()
                .filter(version -> version.getEffectiveDate().isAfter(sectionDTO.getExpirationDate())).collect(Collectors.toList());
        removedCustomerAgreementContractSectionAccountDTOs.forEach(accountDTO -> {
            CustomerAgreementContractSectionAccount sectionAccount = existingCustomerAgreementContractSection.getCustomerAgreementContractSectionAccounts().stream().
                    filter(deletedAccount -> accountDTO.getCustomerAgreementContractSectionAccountID().equals(deletedAccount.getCustomerAgreementContractSectionAccountID()))
                    .findFirst().orElse(null);
            if (sectionAccount != null && ((sectionDTO.getEffectiveDate().isEqual(sectionAccount.getEffectiveDate())
                    || sectionDTO.getEffectiveDate().isBefore(sectionAccount.getEffectiveDate()))
                    && (sectionDTO.getExpirationDate().isEqual(sectionAccount.getExpirationDate())
                    || sectionDTO.getExpirationDate().isAfter(sectionAccount.getExpirationDate())))) {
                sectionAccount.setInvalidIndicator("Y");
                sectionAccount.setInvalidReasonType(invalidReasonType);
            } else if (sectionAccount != null && sectionDTO.getEffectiveDate().isAfter(sectionAccount.getEffectiveDate())) {
                if (!CollectionUtils.isEmpty(versions) && sectionAccount.getExpirationDate().isAfter(sectionDTO.getExpirationDate())) {
                    CustomerAgreementContractSectionAccount account = customerAgreementContractSectionMapper.cloneAccount(sectionAccount);
                    account.setCustomerAgreementContractSectionAccountID(null);
                    account.setEffectiveDate(versions.stream().findFirst().get().getEffectiveDate());
                    existingCustomerAgreementContractSection.getCustomerAgreementContractSectionAccounts().add(account);
                }
                sectionAccount.setExpirationDate(sectionDTO.getEffectiveDate().minusDays(1));
            } else if (sectionAccount != null && sectionDTO.getExpirationDate().isBefore(sectionAccount.getExpirationDate())) {
                sectionAccount.setEffectiveDate(sectionDTO.getExpirationDate().plusDays(1));
            }
        });
    }

    private void applyDateRules(CustomerAgreementContractSection existingCustomerAgreementContractSection,
                                CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,CustomerAgreementChildUpdateDTO customerAgreementChildUpdateDTO) {
        InvalidReasonType invalidReasonType = new InvalidReasonType();
        invalidReasonType.setInvalidReasonTypeID(3);
        invalidReasonType.setInvalidReasonTypeName("Deleted");
        existingCustomerAgreementContractSection.getCustomerAgreementContractSectionVersions().forEach(version -> {
            if (version.getInvalidReasonType() != null && version.getInvalidReasonType().getInvalidReasonTypeID() != 3 &&
                    version.getCustomerAgreementContractSectionVersionID() != null &&
                    !version.getCustomerAgreementContractSectionVersionID().equals(customerAgreementContractSectionDTO.getCustomerAgreementContractSectionVersionID())) {
                if ((customerAgreementContractSectionDTO.getEffectiveDate().isAfter(version.getEffectiveDate()))
                        && (customerAgreementContractSectionDTO.getExpirationDate().isEqual(version.getExpirationDate())
                        || customerAgreementContractSectionDTO.getExpirationDate().isBefore(version.getExpirationDate()))) {
                    version.setExpirationDate(customerAgreementContractSectionDTO.getEffectiveDate().minusDays(1));
                } else if ((customerAgreementContractSectionDTO.getEffectiveDate().isEqual(version.getExpirationDate())
                        || customerAgreementContractSectionDTO.getEffectiveDate().isBefore(version.getExpirationDate()))
                        && (customerAgreementContractSectionDTO.getExpirationDate().isEqual(version.getEffectiveDate())
                        || customerAgreementContractSectionDTO.getExpirationDate().isAfter(version.getEffectiveDate()))) {
                    version.setInvalidIndicator("Y");
                    version.setInvalidReasonType(invalidReasonType);
                    customerAgreementChildUpdateDTO.getDeletedVersionIDs().add(version.getCustomerAgreementContractSectionVersionID());
                }
            }
        });
    }


    private boolean isGracePeriodEdit(CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion) {
        List<ConfigurationParameterDetail> configurationParameterDetails =
                pricingConfigurationService.getDateRelatedConfigurations();
        return isValidGracePeriod(existingCustomerAgreementContractSectionVersion.getCreateTimestamp().toLocalDate(),
                getGracePeriodConfiguration(configurationParameterDetails));
    }


    private void setSectionType(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO, CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion) {
        SectionType sectionType = new SectionType();
        sectionType.setSectionTypeID(
                Integer.valueOf(
                        customerAgreementContractSectionDTO.getCustomerAgreementContractSectionTypeID()));
        sectionType.setSectionTypeName(
                customerAgreementContractSectionDTO.getCustomerAgreementContractSectionTypeName());
        existingCustomerAgreementContractSectionVersion.setSectionType(sectionType);
    }

    private void setSectionName(CustomerAgreementContractSection existingCustomerAgreementContractSection) {
        List<CustomerAgreementContractSectionVersion> customerAgreementContractSectionVersions = existingCustomerAgreementContractSection.getCustomerAgreementContractSectionVersions()
                .stream().filter(customerAgreementContractSectionVersion -> "N".equalsIgnoreCase(customerAgreementContractSectionVersion.getInvalidIndicator())).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(customerAgreementContractSectionVersions)) {
            customerAgreementContractSectionVersions.sort(Comparator.comparing(CustomerAgreementContractSectionVersion::getExpirationDate));
            String sectionName = customerAgreementContractSectionVersions.get(customerAgreementContractSectionVersions.size() - 1).getCustomerAgreementContractSectionName();
            existingCustomerAgreementContractSection.setCustomerAgreementContractSectionName(sectionName);
        }
    }

    private void updateContract(CustomerAgreementContractSection existingCustomerAgreementContractSection, List<String> changedProperties, Integer contractId) {
        if (changedProperties.contains("customerAgreementContractID")) {
            CustomerAgreementContract customerAgreementContract =
                    customerAgreementContractRepository.findOne(
                            contractId);
            existingCustomerAgreementContractSection.setCustomerAgreementContract(customerAgreementContract);
        }
    }


    private List<String> getChangedProperties(Section newSection, Section existingSection) {
        Javers javers = JaversBuilder.javers()
                .withListCompareAlgorithm(ListCompareAlgorithm.LEVENSHTEIN_DISTANCE)
                .build();
        Diff diff = javers.compare(newSection, existingSection);

        return diff.getChangesByType(PropertyChange.class).stream().map(PropertyChange::getPropertyName).collect(Collectors.toList());
    }

    private void validateName(Integer customerAgreementID, CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO, List<String> changedProperties, Integer sectionId) {

        if (changedProperties.contains("customerAgreementContractSectionName") && !CollectionUtils.isEmpty(customerAgreementContractSectionVersionRepository
                .validateName(
                        customerAgreementContractSectionDTO.getCustomerAgreementContractSectionName(), customerAgreementID, sectionId, "N"))) {
            throw new JBHValidationException(SECTION_NAME_ALREADY_EXISTS);
        }

    }


    private List<CustomerAgreementContractSectionAccount>
    prepareCustomerAgreementContractSectionAccounts(
            List<CustomerAgreementContractSectionAccountDTO>
                    newlySelectedCustomerAgreementContractSectionAccountDTOs,
            CustomerAgreementContractSection existingCustomerAgreementContractSection,
            InvalidReasonType invalidReasonType) {
        List<CustomerAgreementContractSectionAccount>
                newlySelectedCustomerAgreementContractSectionAccount = null;
        if (!CollectionUtils.isEmpty(newlySelectedCustomerAgreementContractSectionAccountDTOs)) {

            newlySelectedCustomerAgreementContractSectionAccount =
                    customerAgreementContractSectionMapper.fromCustomerAgreementContractSectionAccountDTOs(
                            newlySelectedCustomerAgreementContractSectionAccountDTOs);
            existingCustomerAgreementContractSection.getCustomerAgreementContractSectionAccounts()
                    .addAll(newlySelectedCustomerAgreementContractSectionAccount);
            existingCustomerAgreementContractSection
                    .getCustomerAgreementContractSectionAccounts()
                    .forEach(
                            customerAgreementContractSectionAccount ->
                                    constructCustomerAgreementContractSectionAccount(
                                            existingCustomerAgreementContractSection,
                                            customerAgreementContractSectionAccount,
                                            invalidReasonType));
        }

        return newlySelectedCustomerAgreementContractSectionAccount;
    }


    private boolean isDateChanged(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO, CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion) {
        boolean dateChanged = false;
        if (!customerAgreementContractSectionDTO.getEffectiveDate().isEqual(existingCustomerAgreementContractSectionVersion
                .getEffectiveDate())
                || !customerAgreementContractSectionDTO.getExpirationDate().isEqual(existingCustomerAgreementContractSectionVersion
                .getExpirationDate())) {
            dateChanged = true;
        }
        log.info("Isdatechanged" + dateChanged);
        return dateChanged;
    }

    private void validateDateRules(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        List<ConfigurationParameterDetail> configurationParameterDetails =
                pricingConfigurationService.getDateRelatedConfigurations();
        isValidEffectiveAndExpirationDate(customerAgreementContractSectionDTO.getEffectiveDate(), customerAgreementContractSectionDTO.getExpirationDate());
        validateExpirationDate(customerAgreementContractSectionDTO.getExpirationDate());
        validateSectionDate(customerAgreementContractSectionDTO);
        isValidBackAndFutureDates(
                customerAgreementContractSectionDTO.getEffectiveDate(),
                customerAgreementContractSectionDTO.getExpirationDate(), configurationParameterDetails);
    }

    private void deleteRemovedBillToCodes(
            List<CustomerAgreementContractSectionAccountDTO> removedCustomerAgreementContractSectionAccountDTOs) {
        if (!CollectionUtils.isEmpty(removedCustomerAgreementContractSectionAccountDTOs)) {
            customerAgreementSectionAccountRepository.deleteRemovedBillToCodes(
                    removedCustomerAgreementContractSectionAccountDTOs.stream()
                            .map(CustomerAgreementContractSectionAccountDTO::getCustomerAgreementContractSectionAccountID).collect(Collectors.toList()));
        }
    }

    private String getExistingUniqueDocId(
            Integer customerAgreementID,
            Integer customerAgreementContractID,
            Integer customerAgreementContractSectionID,
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO
    ) {
        String existingUniqueDocID = null;
        if (customerAgreementContractSectionDTO.getIsContractChanged()) {
            existingUniqueDocID =
                    prepareUniqueDocID(
                            customerAgreementID, customerAgreementContractID, customerAgreementContractSectionID);
        }
        return existingUniqueDocID;
    }

    private void checkNewlyAddedBillToCodes(
            List<CustomerAgreementContractSectionAccountDTO>
                    newlySelectedCustomerAgreementContractSectionAccountDTOs,
            CustomerAgreementContractSectionAccountDTO customerAgreementContractSectionAccountDTO, CustomerAgreementContractSection existingCustomerAgreementContractSection) {
        if (customerAgreementContractSectionAccountDTO.getCustomerAgreementContractSectionAccountID()
                == null) {
            CustomerAgreementContractSectionAccount account = existingCustomerAgreementContractSection.getCustomerAgreementContractSectionAccounts()
                    .stream()
                    .filter(existingAccount ->
                            (existingAccount.getBillingPartyID().equals(customerAgreementContractSectionAccountDTO.getBillingPartyID()) &&
                                    existingAccount.getEffectiveDate().isEqual(customerAgreementContractSectionAccountDTO.getEffectiveDate())))
                    .findFirst().orElse(null);
            if (!Objects.isNull(account)) {
                updateAccount(customerAgreementContractSectionAccountDTO, account);
            } else {
                customerAgreementContractSectionAccountDTO.setIsAdded(true);
                newlySelectedCustomerAgreementContractSectionAccountDTOs.add(customerAgreementContractSectionAccountDTO);
            }

        }
    }

    private void updateAccount(CustomerAgreementContractSectionAccountDTO customerAgreementContractSectionAccountDTO, CustomerAgreementContractSectionAccount existingCustomerSection) {

        existingCustomerSection.setExpirationDate(customerAgreementContractSectionAccountDTO.getExpirationDate());
        existingCustomerSection.setInvalidIndicator("N");
    }


    private void isBillToRemoved(
            List<CustomerAgreementContractSectionAccountDTO> removedCustomerAgreementContractSectionAccountIDs,
            CustomerAgreementContractSectionAccountDTO customerAgreementContractSectionAccountDTO) {
        if (customerAgreementContractSectionAccountDTO.getIsRemoved() != null && customerAgreementContractSectionAccountDTO.getIsRemoved()
                && customerAgreementContractSectionAccountDTO.getCustomerAgreementContractSectionAccountID()
                != null) {
            removedCustomerAgreementContractSectionAccountIDs.add(
                    customerAgreementContractSectionAccountDTO);
        }
    }

    private CustomerAgreementContractSectionAccount constructCustomerAgreementContractSectionAccount(
            CustomerAgreementContractSection existingCustomerAgreementContractSection,
            CustomerAgreementContractSectionAccount customerAgreementContractSectionAccount,
            InvalidReasonType invalidReasonType) {
        customerAgreementContractSectionAccount.setCustomerAgreementContractSection(
                existingCustomerAgreementContractSection);
        customerAgreementContractSectionAccount.setInvalidIndicator(INVALID_INDICATOR_NO);
        customerAgreementContractSectionAccount.setInvalidReasonType(invalidReasonType);
        return customerAgreementContractSectionAccount;
    }


    private void prepareCustomerAgreementContractSectionAccountDTOs(
            List<CustomerAgreementContractSectionAccountDTO> accountDTOsFromAccountHierarchy,
            List<CustomerAgreementContractSectionAccountDTO>
                    responseCustomerAgreementContractSectionAccountDTOs,
            CustomerAgreementContractSectionAccount customerAgreementContractSectionAccount) {
        accountDTOsFromAccountHierarchy.forEach(
                accountDTOFromAccountHierarchy -> {
                    if (customerAgreementContractSectionAccount
                            .getBillingPartyID()
                            .equals(accountDTOFromAccountHierarchy.getBillingPartyID())) {
                        accountDTOFromAccountHierarchy.setCustomerAgreementContractSectionAccountID(
                                customerAgreementContractSectionAccount
                                        .getCustomerAgreementContractSectionAccountID());
                        accountDTOFromAccountHierarchy.setEffectiveDate(
                                customerAgreementContractSectionAccount.getEffectiveDate());
                        accountDTOFromAccountHierarchy.setExpirationDate(
                                customerAgreementContractSectionAccount.getExpirationDate());
                        responseCustomerAgreementContractSectionAccountDTOs.add(accountDTOFromAccountHierarchy);
                    }
                });
    }

    private List<CustomerAgreementContractSectionAccountDTO> getAccountDTOsFromAccountHierarchy(
            Integer customerAgreementID) {
        return customerAgreementSectionAccountService.fetchAccountDTOsFromAccountHierarchy(
                customerAgreementID);
    }


    private CustomerAgreementContractVersion getCustomerAgreementContractVersion(Integer contractID) {
        CustomerAgreementContract customerAgreementContract =
                customerAgreementContractRepository.findOne(contractID);
        return customerAgreementContract.getCustomerAgreementContractVersions().get(0);
    }

    public List<CustomerAgreementContractSectionAccountDTO> fetchCustomerAgreementContractSection(
            Integer customerAgreementID,
            Integer contractID,
            Integer sectionID,
            LocalDate effectiveDate,
            LocalDate expirationDate) {
        List<CustomerAgreementContractSectionAccountDTO> accountDTOsFromAccountHierarchy =
                getAccountDTOsFromAccountHierarchy(customerAgreementID);

        CustomerAgreementContractSection customerAgreementContractSection =
                getCustomerAgreementContractSection(sectionID);

        List<CustomerAgreementContractSectionAccount> customerAgreementContractSectionAccounts =
                customerAgreementContractSection.getCustomerAgreementContractSectionAccounts();

        List<CustomerAgreementContractSectionAccountDTO>
                responseCustomerAgreementContractSectionAccountDTOs = new ArrayList<>();

        if (!customerAgreementContractSectionAccounts.isEmpty()) {
            customerAgreementContractSectionAccounts.forEach(
                    customerAgreementContractSectionAccount ->
                            prepareCustomerAgreementContractSectionAccountDTOs(
                                    accountDTOsFromAccountHierarchy,
                                    responseCustomerAgreementContractSectionAccountDTOs,
                                    customerAgreementContractSectionAccount));
        }

        List<CustomerAgreementContractSectionAccountDTO> availableBillTos =
                getAvailableBillTos(customerAgreementID, effectiveDate, expirationDate);

        responseCustomerAgreementContractSectionAccountDTOs.addAll(availableBillTos);

        return responseCustomerAgreementContractSectionAccountDTOs;
    }

    private List<CustomerAgreementContractSectionAccountDTO> getAvailableBillTos(
            Integer customerAgreementID, LocalDate effectiveDate, LocalDate expirationDate) {
        return customerAgreementSectionAccountService.fetchBillingPartyDetails(
                customerAgreementID, effectiveDate, expirationDate);
    }

    private void isValidBackAndFutureDates(
            LocalDate requestEffectiveDate, LocalDate requestExpirationDate, List<ConfigurationParameterDetail> configurationParameterDetails) {
        if (configurationParameterDetails == null) {
            configurationParameterDetails = pricingConfigurationService.getDateRelatedConfigurations();
        }
        validateBackDate(requestEffectiveDate, requestExpirationDate, configurationParameterDetails);
        validateFutureDate(requestEffectiveDate, configurationParameterDetails);
    }

    private void validateExpirationDate(LocalDate requestExpirationDate) {
        if (!(requestExpirationDate.isBefore(
                LocalDate.parse(pricingAgreementProperties.getExpirationDate()))
                || requestExpirationDate.isEqual(
                LocalDate.parse(pricingAgreementProperties.getExpirationDate())))) {
            throw new JBHValidationException(INVALID_DATE_RANGE_FOR_SECTION);
        }
    }

    private void updateCargoDateRanges(
            Integer customerAgreementContractSectionID,
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO,
            boolean attributeChanged) {
        if (attributeChanged) {
            customerSectionCargoService.updateSectionCargos(
                    customerAgreementContractSectionID,
                    customerAgreementContractSectionDTO.getEffectiveDate(),
                    customerAgreementContractSectionDTO.getExpirationDate());
        }
    }

    private String prepareUniqueDocID(
            Integer customerAgreementID,
            Integer customerAgreementContractID,
            Integer customerAgreementContractSectionID) {
        StringBuilder uniqueDocID = new StringBuilder();
        uniqueDocID
                .append(customerAgreementID)
                .append(customerAgreementContractID)
                .append(customerAgreementContractSectionID);
        return uniqueDocID.toString();
    }

    private void isValidBillingPartyIDs(
            List<CustomerAgreementContractSectionAccountDTO>
                    newlySelectedCustomerAgreementContractSectionAccountDTOs,
            Integer sectionID) {
        if (!CollectionUtils.isEmpty(newlySelectedCustomerAgreementContractSectionAccountDTOs)) {
            CustomerAgreementContractSectionAccountDTO account = newlySelectedCustomerAgreementContractSectionAccountDTOs.stream().findFirst().orElse(null);
            if (account != null && !customerAgreementSectionAccountService
                    .fetchExistingBillingPartyWithOtherSections(
                            newlySelectedCustomerAgreementContractSectionAccountDTOs
                                    .stream()
                                    .map(sectionAccount -> sectionAccount.getBillingPartyID())
                                    .collect(Collectors.toList()),
                            account.getEffectiveDate(),
                            account.getExpirationDate(),
                            sectionID)
                    .isEmpty()) {
                throw new JBHValidationException(BILL_TO_CODE_ALREADY_EXISTS);
            }
        }
    }

    public CustomerAgreementContractSectionVersion customerAgreementContractSectionVersion(
            Integer customerAgreementContractSectionID, String invalidIndicator) {
        return customerAgreementContractSectionVersionRepository
                .findTopByCustomerAgreementContractSectionCustomerAgreementContractSectionIDAndInvalidIndicator(
                        customerAgreementContractSectionID, invalidIndicator);
    }

    public CustomerSectionDetailDTO fetchAgreementSectionDetails(Integer sectionID, LocalDate expirationDate) {

        CustomerAgreementContractSectionVersion customerAgreementContractSectionVersion =
                customerAgreementContractSectionVersionRepository.fetchSectionVersionName(sectionID, expirationDate);

        CustomerSectionDetailDTO customerSectionDetailDTO =
                customerAgreementContractSectionVersionMapper.getCustomerSectionDetailDTO(
                        customerAgreementContractSectionVersion);
        CustomerAgreementContract contract =
                customerAgreementContractSectionVersion
                        .getCustomerAgreementContractSection()
                        .getCustomerAgreementContract();
        Optional<CustomerAgreementContractVersion> activeContractVersionOpt =
                contract
                        .getCustomerAgreementContractVersions()
                        .stream()
                        .filter(contractVersion -> contractVersion.getInvalidIndicator().equals("N"))
                        .findAny();
        activeContractVersionOpt.ifPresent(
                activeContractVersion -> {
                    customerSectionDetailDTO.setCustomerContractNumber(
                            activeContractVersion.getCustomerContractNumber());
                });
        return customerSectionDetailDTO;
    }

    private String getSectionStatus(
            CustomerAgreementContractSectionVersion customerAgreementContractSectionVersion) {
        if (customerAgreementContractSectionVersion
                .getInvalidIndicator()
                .equalsIgnoreCase(INVALID_INDICATOR_NO)
                && customerAgreementContractSectionVersion.getExpirationDate().compareTo(LocalDate.now())
                >= 0) {
            return INVALID_REASON_ACTIVE;
        } else if ((customerAgreementContractSectionVersion
                .getInvalidIndicator()
                .equalsIgnoreCase(INVALID_INDICATOR_NO)
                && customerAgreementContractSectionVersion
                .getExpirationDate()
                .compareTo(LocalDate.now())
                < 0)
                || (customerAgreementContractSectionVersion
                .getInvalidIndicator()
                .equalsIgnoreCase(INVALID_INDICATOR_YES)
                && customerAgreementContractSectionVersion
                .getInvalidReasonType()
                .getInvalidReasonTypeName()
                .equalsIgnoreCase(INACTIVE))) {
            return INVALID_REASON_INACTIVE;
        } else if (customerAgreementContractSectionVersion
                .getInvalidIndicator()
                .equalsIgnoreCase(INVALID_INDICATOR_YES)
                && customerAgreementContractSectionVersion
                .getInvalidReasonType()
                .getInvalidReasonTypeName()
                .equalsIgnoreCase(CustomerAgreementConstants.DELETED)) {
            return CustomerAgreementConstants.DELETED;
        }
        return EMPTY;
    }

    public List<String> fetchCurrencyCode(
            List<Integer> sectionIDs, LocalDate effectiveDate, LocalDate expirationDate) {
        if (Objects.nonNull(effectiveDate) && Objects.nonNull(expirationDate)) {
            return Optional.ofNullable(
                    customerAgreementContractSectionRepository.fetchSectionCurrencyCodeByDate(
                            sectionIDs, effectiveDate, expirationDate))
                    .filter(list -> !list.isEmpty())
                    .orElse(null);
        } else {
            return Optional.ofNullable(
                    customerAgreementContractSectionRepository.fetchCurrencyCode(sectionIDs))
                    .filter(list -> !list.isEmpty())
                    .orElse(null);
        }
    }

    public CustomerAgreementContractSectionViewDTO fetchCustomerAgreementContractSectionByVersionID(
            Integer customerAgreementID, Integer contractID, Integer sectionID, Integer sectionVersionID) {

        List<CustomerAgreementContractSectionAccountDTO> accountDTOsFromAccountHierarchy =
                getAccountDTOsFromAccountHierarchy(customerAgreementID);

        CustomerAgreementContractSectionVersion customerAgreementContractSectionVersion =
                getCustomerAgreementContractSectionVersion(sectionVersionID);

        CustomerAgreementContractVersion customerAgreementContractVersion =
                getCustomerAgreementContractVersion(
                        customerAgreementContractSectionVersion
                                .getCustomerAgreementContractSection()
                                .getCustomerAgreementContract()
                                .getCustomerAgreementContractID());

        List<CustomerAgreementContractSectionAccount> customerAgreementContractSectionAccounts =
                customerAgreementContractSectionVersion.getCustomerAgreementContractSection().getCustomerAgreementContractSectionAccounts();

        List<CustomerAgreementContractSectionAccountDTO>
                responseCustomerAgreementContractSectionAccountDTOs = new ArrayList<>();

        if (!customerAgreementContractSectionAccounts.isEmpty()) {
            customerAgreementContractSectionAccounts.forEach(
                    account -> {
                        if ("N".equalsIgnoreCase(account.getInvalidIndicator())
                                && ((customerAgreementContractSectionVersion.getEffectiveDate().isEqual(account.getExpirationDate())
                                || customerAgreementContractSectionVersion.getEffectiveDate().isBefore(account.getExpirationDate()))
                                && (customerAgreementContractSectionVersion.getExpirationDate().isEqual(account.getEffectiveDate())
                                || customerAgreementContractSectionVersion.getExpirationDate().isAfter(account.getEffectiveDate())))
                                ) {
                            prepareCustomerAgreementContractSectionAccountDTOs(
                                    accountDTOsFromAccountHierarchy,
                                    responseCustomerAgreementContractSectionAccountDTOs,
                                    account);
                        }
                    }
            );

        }

        return customerAgreementContractSectionMapper
                .createFetchCustomerAgreementContractSectionViewDTO(
                        customerAgreementContractVersion,
                        customerAgreementContractSectionVersion.getCustomerAgreementContractSection(),
                        customerAgreementContractSectionVersion,
                        responseCustomerAgreementContractSectionAccountDTOs,
                        customerAgreementContractSectionVersion.getEffectiveDate(),
                        customerAgreementContractSectionVersion.getExpirationDate(),
                        getSectionStatus(customerAgreementContractSectionVersion));
    }


    private CustomerAgreementContractSectionVersion getCustomerAgreementContractSectionVersion(Integer sectionVersionID) {
        return customerAgreementContractSectionVersionRepository.findByCustomerAgreementContractSectionVersionID(sectionVersionID);
    }

    public List<CustomerAgreementContractSectionLookUpDTO> getAllAgreementSectionLookupDetails(
            Integer customerAgreementID,
            String status) {

        return customerAgreementContractSectionRepository.getAllAgreementSectionLookupDetails(customerAgreementID,
                status.equalsIgnoreCase(INVALID_REASON_ACTIVE)
                        ? INVALID_INDICATOR_NO
                        : INVALID_INDICATOR_YES,
                status.equalsIgnoreCase(INVALID_REASON_ACTIVE)
                        ? INVALID_REASON_ACTIVE
                        : INVALID_REASON_INACTIVE);
    }

    private void validateEffectiveAndExpirationDate(CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        boolean isValidDate =
                isValidDateRange(
                        customerAgreementContractSectionDTO.getEffectiveDate(), customerAgreementContractSectionDTO.getExpirationDate());
        if (!isValidDate) {
            throw new JBHValidationException(EFFECTIVE_DATE_GREATER_THAN_EXPIRATION_DATE);
        }
    }

    public void validateSectionDetails(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO, List<ConfigurationParameterDetail> configurationParameterDetails) {
        validateSectionType(
                customerAgreementContractSectionDTO.getCustomerAgreementContractSectionTypeName());
        if (!CollectionUtils.isEmpty(customerAgreementContractSectionVersionRepository.validateName(
                customerAgreementContractSectionDTO.getCustomerAgreementContractSectionName(), customerAgreementContractSectionDTO.getCustomerAgreementContractDTO().getCustomerAgreementID(), customerAgreementContractSectionDTO.getCustomerAgreementContractSectionID(), "N"))) {
            throw new JBHValidationException(SECTION_NAME_ALREADY_EXISTS);
        }
        validateEffectiveAndExpirationDate(customerAgreementContractSectionDTO);
        validateSectionDate(customerAgreementContractSectionDTO);
        isValidBackAndFutureDates(
                customerAgreementContractSectionDTO.getEffectiveDate(),
                customerAgreementContractSectionDTO.getExpirationDate(), configurationParameterDetails);

        if (customerAgreementContractSectionDTO.getCustomerAgreementContractSectionID() != null
                && customerAgreementContractSectionDTO.getCustomerAgreementContractSectionVersionID() != null) {

            CustomerAgreementContractSection existingCustomerAgreementContractSection = getCustomerAgreementContractSection(customerAgreementContractSectionDTO.getCustomerAgreementContractSectionID());
            boolean isAgreementInDraftStatus = isAgreementInDraftStatus(existingCustomerAgreementContractSection);
            CustomerAgreementContractSectionVersion existingCustomerAgreementContractSectionVersion = getCustomerAgreementContractSectionVersion(customerAgreementContractSectionDTO.getCustomerAgreementContractSectionVersionID(), existingCustomerAgreementContractSection);
            if (existingCustomerAgreementContractSectionVersion != null) {
                validateLatestRecord(
                        customerAgreementContractSectionDTO,
                        isAgreementInDraftStatus,
                        existingCustomerAgreementContractSectionVersion);
                Section newSection = sectionMapper.getSection(customerAgreementContractSectionDTO);
                Section existingSection =
                        sectionMapper.getSection(existingCustomerAgreementContractSectionVersion);
                List<String> changedProperties = getChangedProperties(newSection, existingSection);
                validateEffectiveDate(customerAgreementContractSectionDTO, existingCustomerAgreementContractSectionVersion);
                newSection.isValidRecord(existingCustomerAgreementContractSectionVersion);
                boolean isGracePeriodEdit = isGracePeriodEdit(existingCustomerAgreementContractSectionVersion);
                validateGracePeriod(customerAgreementContractSectionDTO, existingCustomerAgreementContractSectionVersion, changedProperties, isGracePeriodEdit);
            }
        }
    }

    private CustomerAgreementContractSection getCustomerAgreementContractSection(Integer customerAgreementContractSectionID) {
        return customerAgreementContractSectionRepository.findOne(customerAgreementContractSectionID);
    }

    private void validateSectionBillToCodeDetails(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO, List<ConfigurationParameterDetail> configurationParameterDetails) {
        customerAgreementContractSectionDTO.getCustomerAgreementContractSectionAccountDTOs()
                .forEach(customerAgreementContractSectionAccountDTO -> {
                    validateEffectiveAndExpirationDateForBillToCodes(customerAgreementContractSectionAccountDTO);
                    if (!isValidDate(customerAgreementContractSectionDTO.getEffectiveDate(), customerAgreementContractSectionDTO.getExpirationDate(),
                            customerAgreementContractSectionAccountDTO.getEffectiveDate(), customerAgreementContractSectionAccountDTO.getExpirationDate())) {
                        throw new JBHValidationException(INVALID_DATE_RANGE_FOR_BILL_TO_CODES);
                    }
                    isValidBackAndFutureDates(
                            customerAgreementContractSectionAccountDTO.getEffectiveDate(),
                            customerAgreementContractSectionAccountDTO.getExpirationDate(), configurationParameterDetails);
                });


    }

    private void validateEffectiveAndExpirationDateForBillToCodes(CustomerAgreementContractSectionAccountDTO customerAgreementContractSectionAccountDTO) {
        boolean isValidDate =
                isValidDateRange(
                        customerAgreementContractSectionAccountDTO.getEffectiveDate(), customerAgreementContractSectionAccountDTO.getExpirationDate());
        if (!isValidDate) {
            throw new JBHValidationException(EFFECTIVE_DATE_GREATER_THAN_EXPIRATION_DATE);
        }
    }

    private List<CustomerAgreementContractSectionAccount> fetchSectionsWithBillTosAssigned(
            CustomerAgreementContractSectionDTO customerAgreementContractSectionDTO) {
        List<Integer> billingPartyIDs = new ArrayList<>();
        customerAgreementContractSectionDTO
                .getCustomerAgreementContractSectionAccountDTOs()
                .forEach(
                        customerAgreementContractSectionAccountDTO -> {
                            if (Objects.nonNull(
                                    customerAgreementContractSectionAccountDTO
                                            .getBillingPartyID())) {
                                billingPartyIDs.add(customerAgreementContractSectionAccountDTO.getBillingPartyID());
                            }
                        });
        List<CustomerAgreementContractSectionAccount> customerAgreementContractSectionAccounts =
                customerAgreementSectionAccountService.getSectionAccountDetails(
                        billingPartyIDs, CustomerAgreementConstants.INVALID_INDICATOR);
        return customerAgreementContractSectionAccounts;
    }

    private List<CustomerAgreementContractSectionAccount> getModifiedSectionAccounts(
            List<CustomerAgreementContractSectionAccount> customerAgreementContractSectionAccounts,
            List<CustomerAgreementContractSectionAccountDTO>
                    customerAgreementContractSectionAccountDTOs) {
        List<CustomerAgreementContractSectionAccount> sectionAccountList = new ArrayList<>();
        customerAgreementContractSectionAccountDTOs.forEach(
                customerAgreementContractSectionAccountDTO -> {
                    List<CustomerAgreementContractSectionAccount> existingSectionAccountsForGivenBillTos =
                            customerAgreementContractSectionAccounts.stream()
                                    .filter(
                                            customerAgreementContractSectionAccount ->
                                                    customerAgreementContractSectionAccount
                                                            .getBillingPartyID()
                                                            .equals(
                                                                    customerAgreementContractSectionAccountDTO.getBillingPartyID()))
                                    .collect(Collectors.toList());
                    if (!CollectionUtils.isEmpty(existingSectionAccountsForGivenBillTos)) {
                        existingSectionAccountsForGivenBillTos.forEach(
                                sectionAccount -> {
                                    if (isBillTosAlreadyAssigned(
                                            sectionAccount, customerAgreementContractSectionAccountDTO)) {
                                        getModifiedSectionAccounts(sectionAccountList, customerAgreementContractSectionAccountDTO, existingSectionAccountsForGivenBillTos, sectionAccount);
                                    }
                                });
                    }
                });
        return sectionAccountList;
    }

    private void getModifiedSectionAccounts(List<CustomerAgreementContractSectionAccount> sectionAccountList, CustomerAgreementContractSectionAccountDTO customerAgreementContractSectionAccountDTO, List<CustomerAgreementContractSectionAccount> existingSectionAccountsForGivenBillTos,
                                            CustomerAgreementContractSectionAccount sectionAccount) {
        if (isNewSectionAccountBothExpAndEffWithinExistingSectionAccountDateRange(
                customerAgreementContractSectionAccountDTO.getEffectiveDate(),
                customerAgreementContractSectionAccountDTO.getExpirationDate(),
                sectionAccount.getEffectiveDate(),
                sectionAccount.getExpirationDate())) {
            sectionAccountList.add(endDateSectionAccount(sectionAccount));
        } else if (isNewSectionAccountEffectiveDateWithinExistingSectionAccountDateRange(
                customerAgreementContractSectionAccountDTO.getEffectiveDate(),
                sectionAccount.getEffectiveDate(),
                sectionAccount.getExpirationDate())) {
            sectionAccount.setExpirationDate(
                    customerAgreementContractSectionAccountDTO
                            .getEffectiveDate()
                            .minusDays(1));
            sectionAccountList.add(sectionAccount);
        } else if (isNewSectionAccountDateIsOverlappingOnExistingBillTo(customerAgreementContractSectionAccountDTO, sectionAccount)) {
            getUpdatedSectionAccountForOverlappingDates(sectionAccountList, customerAgreementContractSectionAccountDTO, sectionAccount);
        } else if (customerAgreementContractSectionAccountDTO.getExpirationDate().isEqual(sectionAccount.getEffectiveDate())) {
            sectionAccountList.add(endDateSectionAccount(sectionAccount));
        } else {
            if (isNewSectionAccountExpirationDateWithinExistingSectionAccountDateRange(
                    customerAgreementContractSectionAccountDTO.getExpirationDate(),
                    sectionAccount.getEffectiveDate(),
                    sectionAccount.getExpirationDate())) {
                sectionAccountList.addAll(
                        endDateSectionAccount(
                                existingSectionAccountsForGivenBillTos,
                                customerAgreementContractSectionAccountDTO));
            }
        }
    }

    private void getUpdatedSectionAccountForOverlappingDates(
            List<CustomerAgreementContractSectionAccount> sectionAccountList,
            CustomerAgreementContractSectionAccountDTO customerAgreementContractSectionAccountDTO,
            CustomerAgreementContractSectionAccount sectionAccount) {
        if (isExistingBillToDateIsWithinNewEffectiveDate(
                customerAgreementContractSectionAccountDTO, sectionAccount)) {
            sectionAccountList.add(endDateSectionAccount(sectionAccount));
        } else if (sectionAccount.getExpirationDate().isEqual(sectionAccount.getEffectiveDate())
                || customerAgreementContractSectionAccountDTO
                .getEffectiveDate()
                .isEqual(sectionAccount.getEffectiveDate())) {
            sectionAccountList.add(endDateSectionAccount(sectionAccount));
        } else {
            sectionAccount.setExpirationDate(
                    customerAgreementContractSectionAccountDTO.getEffectiveDate().minusDays(1));
            sectionAccountList.add(sectionAccount);
        }
    }

    private boolean isNewSectionAccountDateIsOverlappingOnExistingBillTo(CustomerAgreementContractSectionAccountDTO customerAgreementContractSectionAccountDTO, CustomerAgreementContractSectionAccount sectionAccount) {
        return ((customerAgreementContractSectionAccountDTO
                .getEffectiveDate()
                .isEqual(sectionAccount.getEffectiveDate())) || (customerAgreementContractSectionAccountDTO
                .getEffectiveDate()
                .isEqual(sectionAccount.getExpirationDate())));
    }

    private boolean isExistingBillToDateIsWithinNewEffectiveDate(CustomerAgreementContractSectionAccountDTO customerAgreementContractSectionAccountDTO, CustomerAgreementContractSectionAccount sectionAccount) {
        return (((customerAgreementContractSectionAccountDTO
                .getEffectiveDate()
                .isEqual(sectionAccount.getEffectiveDate())) || (sectionAccount.getEffectiveDate()
                .isAfter(customerAgreementContractSectionAccountDTO
                        .getEffectiveDate()))) && ((customerAgreementContractSectionAccountDTO
                .getExpirationDate()
                .isEqual(sectionAccount.getExpirationDate())) || (sectionAccount.getExpirationDate()
                .isBefore(customerAgreementContractSectionAccountDTO.getExpirationDate()))));
    }

    private CustomerAgreementContractSectionAccount endDateSectionAccount(
            CustomerAgreementContractSectionAccount sectionAccount) {
        sectionAccount.setInvalidIndicator(INVALID_INDICATOR_YES);
        sectionAccount.setInvalidReasonType(
                invalidReasonTypeService.getInvalidReasonTypeByName(
                        pricingAgreementProperties
                                .getInvalidReasonTypes()
                                .get(PricingAgreementConstants.DELETED)));
        return sectionAccount;
    }

    private List<CustomerAgreementContractSectionAccount> endDateSectionAccount(
            List<CustomerAgreementContractSectionAccount> sectionAccounts,
            CustomerAgreementContractSectionAccountDTO customerAgreementContractSectionAccountDTO) {
        List<CustomerAgreementContractSectionAccount> updatedSectionAccount = new ArrayList<>();
        sectionAccounts.forEach(
                sectionAccount -> {
                    if ((sectionAccount.getInvalidIndicator().equalsIgnoreCase("N"))
                            && isNewSectionAccountExpirationDateWithinExistingSectionAccountDateRange(
                            customerAgreementContractSectionAccountDTO.getExpirationDate(),
                            sectionAccount.getEffectiveDate(),
                            sectionAccount.getExpirationDate())) {
                        updatedSectionAccount.add(endDateSectionAccount(sectionAccount));
                    }
                });
        return updatedSectionAccount;
    }

    private boolean isBillTosAlreadyAssigned(
            CustomerAgreementContractSectionAccount customerAgreementContractSectionAccount,
            CustomerAgreementContractSectionAccountDTO customerAgreementContractSectionAccountDTO) {
        return (customerAgreementContractSectionAccount
                .getBillingPartyID()
                .equals(customerAgreementContractSectionAccountDTO.getBillingPartyID()));
    }

    private void postCustomerAgreementContractSectionsToTopic(
            List<AgreementElasticSearchDTO> agreementElasticSearchDTOs,
            String routeName,
            String existingUniqueDocID) {
        CustomerAgreementElasticSearchDTO customerAgreementElasticSearchDTO =
                new CustomerAgreementElasticSearchDTO();
        customerAgreementElasticSearchDTO.setAgreementElasticSearchDTOs(agreementElasticSearchDTOs);
        producerTemplate.requestBody(routeName, customerAgreementElasticSearchDTO);
    }

    public CustomerAgreementContractSectionViewDTO fetchCustomerAgreementContractSectionDetails(
            Integer customerAgreementID, Integer contractID, Integer sectionID) {

        List<CustomerAgreementContractSectionAccountDTO> accountDTOsFromAccountHierarchy =
                getAccountDTOsFromAccountHierarchy(customerAgreementID);
        CustomerAgreementContractSection customerAgreementContractSection =
                getCustomerAgreementContractSection(sectionID);
        CustomerAgreementContractVersion customerAgreementContractVersion =
                getCustomerAgreementContractVersion(
                        customerAgreementContractSection
                                .getCustomerAgreementContract()
                                .getCustomerAgreementContractID());
        List<CustomerAgreementContractSectionAccount> customerAgreementContractSectionAccounts =
                customerAgreementContractSection.getCustomerAgreementContractSectionAccounts();

        List<CustomerAgreementContractSectionAccountDTO>
                responseCustomerAgreementContractSectionAccountDTOs = new ArrayList<>();

        if (!customerAgreementContractSectionAccounts.isEmpty()) {
            customerAgreementContractSectionAccounts.forEach(
                    customerAgreementContractSectionAccount ->
                            prepareCustomerAgreementContractSectionAccountDTOs(
                                    accountDTOsFromAccountHierarchy,
                                    responseCustomerAgreementContractSectionAccountDTOs,
                                    customerAgreementContractSectionAccount));
        }
        CustomerAgreementContractSectionVersion originalCustomerAgreementContractSectionVersion =
                customerAgreementContractSectionVersionRepository
                        .fetchOriginalEffectiveDateAndExpirationDate(sectionID);

        return customerAgreementContractSectionMapper
                .createFetchCustomerAgreementContractSectionViewDTO(
                        customerAgreementContractVersion,
                        customerAgreementContractSection,
                        customerAgreementContractSection.getCustomerAgreementContractSectionVersions().get(0),
                        responseCustomerAgreementContractSectionAccountDTOs,
                        originalCustomerAgreementContractSectionVersion.getEffectiveDate(),
                        originalCustomerAgreementContractSectionVersion.getExpirationDate(),
                        getSectionStatus(originalCustomerAgreementContractSectionVersion));
    }

    public void updatePricingEntities
            (Integer customerAgreementID, Integer sectionID, CustomerAgreementChildUpdateDTO customerAgreementChildUpdateDTO) {

        try {
            List<Integer> customerAgreementContractSectionVersionIDs =
                    customerAgreementChildUpdateDTO.getCustomerAgreementContractSectionVersionIDs();
            List<CustomerAgreementContractSectionVersion> customerAgreementContractSectionVersions =
                    customerAgreementContractSectionVersionRepository
                            .findByCustomerAgreementContractSectionVersionIDIn(
                                    customerAgreementContractSectionVersionIDs);
            CustomerAgreementContractSectionVersion nextVersion =
                    customerAgreementContractSectionVersions.stream()
                            .filter(
                                    version ->
                                            version
                                                    .getEffectiveDate()
                                                    .isAfter(customerAgreementChildUpdateDTO.getExpirationDate()))
                            .findFirst()
                            .orElse(null);
            final LocalDate nextVersionEffectiveDate =
                    nextVersion != null ? nextVersion.getEffectiveDate() : null;
            customerCargoService.updateSectionCargoRelease(
                    customerAgreementID,
                    sectionID,
                    customerAgreementContractSectionVersions,
                    customerAgreementChildUpdateDTO);
            customerCargoService.updateSectionBUCargoReleases(
                    customerAgreementID,
                    sectionID,
                    customerAgreementContractSectionVersions,
                    customerAgreementChildUpdateDTO);
            LineHaulSectionDTO lineHaulSectionDTO = null;
            lineHaulSectionDTO =
                    customerAgreementChildUpdateDTOMapper.getLineHaulSectionDTO(
                            lineHaulSectionDTO, customerAgreementChildUpdateDTO, nextVersionEffectiveDate);
            if (customerAgreementChildUpdateDTO.isExpDateChanged()) {
                lineHaulSectionDTO.setExpirationDate(customerAgreementChildUpdateDTO.getExpirationDate());
            }
            pricingLineHaulClient.updateSectionLineHauls(sectionID, lineHaulSectionDTO);
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
    }

    private void updateCustomerAgreementContractSectionToTopic(
            CustomerAgreementChildUpdateDTO customerAgreementChildUpdateDTO,
            String routeName
    ) {
        producerTemplate.requestBody(routeName, customerAgreementChildUpdateDTO);
    }

    public void rollbackSections(CustomerAgreementChildUpdateDTO customerAgreementChildUpdateDTO, Integer sectionID) {
        CustomerAgreementContractSection existingSection = getCustomerAgreementContractSection(sectionID);
        List<CustomerAgreementContractSectionVersion> customerAgreementContractSectionVersions = existingSection.getCustomerAgreementContractSectionVersions();
        customerAgreementContractSectionVersions.forEach(versions -> {
            if (versions.getExpirationDate().isEqual(customerAgreementChildUpdateDTO.getExpirationDate()) && versions.getInvalidReasonType().getInvalidReasonTypeID() == 3) {

            }
        });
    }
}
