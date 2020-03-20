package com.jbhunt.pricing.agreement.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jbhunt.pricing.agreement.client.ChargeCodeClient;
import com.jbhunt.pricing.agreement.dto.PricingChargeCodeDTO;
import com.jbhunt.pricing.agreement.mapper.PricingChargeCodeMapper;
import com.jbhunt.pricing.agreement.repository.PricingChargeUsageCrossReferenceRepository;
import com.jbhunt.pricing.configuration.dto.ChargeUsageTypeDTO;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class PricingChargeCodeService {

	private final PricingChargeCodeMapper pricingChargeCodeMapper;
	private final PricingChargeUsageCrossReferenceRepository pricingChargeUsageCrossReferenceRepository;
	private final ChargeCodeClient chargeCodeClient;
	                                                                                                                                                          
	@Transactional
	public Integer saveChargeCode(PricingChargeCodeDTO pricingChargeCodeDTO) {
		List<Integer> financeChargeUsageTypeIDs = pricingChargeUsageCrossReferenceRepository
				.fetchByFinanceChargeUsageTypeID(getChargeUsageTypeID(pricingChargeCodeDTO));
		List<Integer> chargeCodeAssociationIDs=chargeCodeClient.saveChargeCode(
				pricingChargeCodeMapper.createChargeTypeEnterpriseDTO(pricingChargeCodeDTO, financeChargeUsageTypeIDs));
		return null;
	}

	private static List<Integer> getChargeUsageTypeID(PricingChargeCodeDTO pricingChargeCodeDTO) {
		return pricingChargeCodeDTO.getChargeUsageTypes().stream().map(ChargeUsageTypeDTO::getChargeUsageTypeID)
				.collect(Collectors.toList());
	}
}
