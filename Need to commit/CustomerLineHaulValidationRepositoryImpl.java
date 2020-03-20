package com.jbhunt.pricing.agreement.repository;

import com.jbhunt.pricing.agreement.dto.CustomerLineHaulAdditionalInformationDTO;
import com.jbhunt.pricing.agreement.entity.CustomerLineHaulConfiguration;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class CustomerLineHaulValidationRepositoryImpl implements CustomerLineHaulValidationRepository{

    String baseQuery ="Select conf from CustomerLineHaulConfiguration conf ";

    @PersistenceContext
    private EntityManager entityManager;

    public List<CustomerLineHaulConfiguration> getDuplicateCustomerLineHaulConfiguration(
            CustomerLineHaulConfiguration customerLineHaulConfiguration,
            CustomerLineHaulAdditionalInformationDTO customerLineHaulAdditionalInformationDTO) {
        StringBuilder stringBuilder = new StringBuilder(baseQuery);

        if(!CollectionUtils.isEmpty(customerLineHaulAdditionalInformationDTO.getCarriers())){
            stringBuilder.append(",CustomerLineHaulCarrier carrier ");
        }
        if(!CollectionUtils.isEmpty(customerLineHaulAdditionalInformationDTO.getBillTos())){
            stringBuilder.append(",CustomerLineHaulAccount account ");
        }
        stringBuilder.append("where conf.financeBusinessUnitServiceOfferingAssociationID = :financeBusinessUnitServiceOfferingAssociationID" +
                " AND conf.serviceOfferingBusinessUnitTransitModeAssociationID = :serviceOfferingBusinessUnitTransitModeAssociationID" +
                " AND conf.customerAgreementContractSectionID = :customerAgreementContractSectionID" +
                " AND conf.equipmentClassificationCode = :equipmentClassificationCode" +
                " AND conf.effectiveDate = :effectiveDate" +
                " AND conf.expirationDate = :expirationDate" +
                " AND conf.customerLineHaulConfigurationStatus.customerLineHaulConfigurationStatusName IN('Pending','Published')" +
                " AND conf.serviceOfferingBusinessUnitTransitModeAssociationID = :serviceOfferingBusinessUnitTransitModeAssociationID" +
                " AND conf.laneID = :laneID " +
                " AND conf.customerLineHaulConfigurationID <> :customerLineHaulConfigurationID");
        if(!CollectionUtils.isEmpty(customerLineHaulAdditionalInformationDTO.getCarriers())){
            stringBuilder.append("AND carrier.customerLineHaulConfiguration.customerLineHaulConfigurationID = conf.customerLineHaulConfigurationID ").append(" AND carrier.carrierID IN (:carrierID)");
        }
        if(!CollectionUtils.isEmpty(customerLineHaulAdditionalInformationDTO.getBillTos())){
            stringBuilder.append("AND account.customerLineHaulConfiguration.customerLineHaulConfigurationID = conf.customerLineHaulConfigurationID ").append(" AND account.billingPartyID IN (:billingPartyID)");
        }
        Query nativeQuery = entityManager.createQuery(stringBuilder.toString());
        nativeQuery.setParameter("financeBusinessUnitServiceOfferingAssociationID", customerLineHaulConfiguration.getFinanceBusinessUnitServiceOfferingAssociationID());
        nativeQuery.setParameter("serviceOfferingBusinessUnitTransitModeAssociationID", customerLineHaulConfiguration.getServiceOfferingBusinessUnitTransitModeAssociationID());
        nativeQuery.setParameter("customerAgreementContractSectionID", customerLineHaulConfiguration.getCustomerAgreementContractSectionID());
        nativeQuery.setParameter("equipmentClassificationCode", customerLineHaulConfiguration.getEquipmentClassificationCode());
        nativeQuery.setParameter("effectiveDate", customerLineHaulConfiguration.getCustomerLineHaulConfigurationStatus().getEffectiveDate());
        nativeQuery.setParameter("expirationDate", customerLineHaulConfiguration.getCustomerLineHaulConfigurationStatus().getExpirationDate());
        nativeQuery.setParameter("laneID", customerLineHaulConfiguration.getLaneID());
        nativeQuery.setParameter("customerLineHaulConfigurationID", customerLineHaulConfiguration.getCustomerLineHaulConfigurationID());
        nativeQuery.setParameter("serviceOfferingBusinessUnitTransitModeAssociationID", customerLineHaulConfiguration.getServiceOfferingBusinessUnitTransitModeAssociationID());
        if(!CollectionUtils.isEmpty(customerLineHaulAdditionalInformationDTO.getCarriers())){
            nativeQuery.setParameter("carrierID" ,customerLineHaulAdditionalInformationDTO.getCarriers().stream().map(carrierDTO -> carrierDTO.getId()).collect(Collectors.toList()));
        }
        if(!CollectionUtils.isEmpty(customerLineHaulAdditionalInformationDTO.getBillTos())){
            nativeQuery.setParameter("billingPartyID" ,customerLineHaulAdditionalInformationDTO.getBillTos().stream().map(billToDetailsDTO -> billToDetailsDTO.getBillToID()).collect(Collectors.toList()));
        }
        return nativeQuery.getResultList();
    }

}






