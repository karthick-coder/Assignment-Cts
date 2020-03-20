package com.jbhunt.pricing.agreement.mapper;

import java.util.stream.Collectors;

import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.jbhunt.pricing.agreement.dto.ChargeTypeBusinessUnitServiceOfferingAssociationDTO;
import com.jbhunt.pricing.agreement.dto.ChargeTypeDTO;
import com.jbhunt.pricing.agreement.dto.ChargeTypeEnterpriseDTO;
import com.jbhunt.pricing.reference.entity.ChargeType;
import com.jbhunt.pricing.reference.entity.ChargeTypeBusinessUnitServiceOfferingAssociation;

@Mapper(componentModel = "spring")
public interface ChargeCodeMapper {

	default ChargeType fromChargeEnterpriseDTO(ChargeTypeEnterpriseDTO chargeTypeEnterpriseDTO) {
		ChargeType chargeType = toChargeType(chargeTypeEnterpriseDTO.getChargeTypeCode(),
				chargeTypeEnterpriseDTO.getChargeTypeDescription(), chargeTypeEnterpriseDTO.getChargeTypeName());
		chargeType.setChargeTypeBusinessUnitServiceOfferingAssociations(chargeTypeEnterpriseDTO
				.getChargeTypeBusinessUnitServiceOfferingAssociations().stream()
				.map(association -> fromChargeTypeBusinessUnitServiceOfferingAssociation(association, chargeType))
				.collect(Collectors.toList()));
		;
		return chargeType;
	}

	default ChargeTypeEnterpriseDTO toChargeTypeEnterpriseDTO(ChargeType chargeType) {
		ChargeTypeEnterpriseDTO chargeTypeEnterpriseDTO = fromChargeType(chargeType);
		chargeTypeEnterpriseDTO.setChargeTypeBusinessUnitServiceOfferingAssociations(chargeType.getChargeTypeBusinessUnitServiceOfferingAssociations().stream()
				.map(association -> toServiceOfferingAssociationDTO(association)).collect(Collectors.toList()));
		return chargeTypeEnterpriseDTO;
	}

	@Mapping(source = "chargeType", target = "chargeType")
	@Mapping(source = "association.financeBusinessUnitServiceOfferingAssociation.financeBusinessUnitServiceOfferingAssociationID", target = "association.financeBusinessUnitServiceOfferingAssociation.financeBusinessUnitServiceOfferingAssociationID")
	@Mapping(source = "association.financeChargeUsageTypeID", target = "financeChargeUsageTypeID")
	@Mapping(source = "association.effectiveDate", target = "effectiveDate")
	@Mapping(source = "association.expirationDate", target = "expirationDate")
	@Mapping(target = "createTimestamp", ignore = true)
	@Mapping(target = "createProgramName", ignore = true)
	@Mapping(target = "createUserId", ignore = true)
	@Mapping(target = "lastUpdateTimestamp", ignore = true)
	@Mapping(target = "lastUpdateProgramName", ignore = true)
	@Mapping(target = "lastUpdateUserId", ignore = true)
	ChargeTypeBusinessUnitServiceOfferingAssociation fromChargeTypeBusinessUnitServiceOfferingAssociation(
			ChargeTypeBusinessUnitServiceOfferingAssociationDTO association, ChargeType chargeType);

	@Mapping(source = "chargeTypeCode", target = "chargeTypeCode")
	@Mapping(source = "chargeTypeName", target = "chargeTypeName")
	@Mapping(source = "chargeTypeDescription", target = "chargeTypeDescription")
	ChargeType toChargeType(String chargeTypeCode, String chargeTypeName, String chargeTypeDescription);

	@InheritInverseConfiguration
	ChargeTypeDTO toChargeTypeDTO(ChargeType chargeType);

	@Mapping(source = "chargeTypeID", target = "chargeTypeID")
	@Mapping(source = "chargeTypeCode", target = "chargeTypeCode")
	@Mapping(source = "chargeTypeName", target = "chargeTypeName")
	@Mapping(source = "chargeTypeDescription", target = "chargeTypeDescription")
	@Mapping(source = "chargeTypeCode", target = "chargeTypeCode")
	ChargeTypeEnterpriseDTO fromChargeType(ChargeType chargeType);

	@Mapping(source = "association.financeChargeUsageTypeID", target = "financeChargeUsageTypeID")
	@Mapping(source = "association.chargeTypeBusinessUnitServiceOfferingAssociationID", target = "chargeTypeBusinessUnitServiceOfferingAssociationID")
	@Mapping(source = "association.effectiveDate", target = "effectiveDate")
	@Mapping(source = "association.expirationDate", target = "expirationDate")
	@Mapping(source = "association.financeBusinessUnitServiceOfferingAssociationID", target = "financeBusinessUnitServiceOfferingAssociationID")
	ChargeTypeBusinessUnitServiceOfferingAssociationDTO toServiceOfferingAssociationDTO(
			ChargeTypeBusinessUnitServiceOfferingAssociation association);

}
