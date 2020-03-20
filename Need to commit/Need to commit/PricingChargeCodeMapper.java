package com.jbhunt.pricing.agreement.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.jbhunt.pricing.agreement.dto.ChargeTypeEnterpriseDTO;
import com.jbhunt.pricing.agreement.dto.PricingChargeCodeDTO;

@Mapper(componentModel = "spring")
public interface PricingChargeCodeMapper {

	@Mapping(source = "pricingChargeCodeDTO.chargeType", target = "chargetype")
	@Mapping(source = "pricingChargeCodeDTO.financeBusinessUnitServiceOfferingAssociations", target = "financeBusinessUnitServiceOfferingAssociations")
	@Mapping(source = "pricingChargeCodeDTO.effectiveDate", target = "effectiveDate")
	@Mapping(source = "pricingChargeCodeDTO.expirationDate", target = "expirationDate")
	@Mapping(source = "financeChargeUsageTypeIDs", target = "financeChargeUsageTypeIDs")
	ChargeTypeEnterpriseDTO createChargeCodeEnterpriseDTO(PricingChargeCodeDTO pricingChargeCodeDTO,
			List<Integer> financeChargeUsageTypeIDs);

	ChargeTypeEnterpriseDTO createChargeTypeEnterpriseDTO(PricingChargeCodeDTO pricingChargeCodeDTO,
			List<Integer> financeChargeUsageTypeIDs);

}
