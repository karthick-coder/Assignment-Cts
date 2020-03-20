package com.jbhunt.pricing.agreement.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jbhunt.pricing.agreement.dto.ChargeTypeEnterpriseDTO;
import com.jbhunt.pricing.agreement.mapper.ChargeCodeMapper;
import com.jbhunt.pricing.agreement.repository.ChargeCodeRepository;
import com.jbhunt.pricing.reference.entity.ChargeType;
import com.jbhunt.pricing.reference.entity.ChargeTypeBusinessUnitServiceOfferingAssociation;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ChargeCodeService {

	private final ChargeCodeRepository chargeCodeRepository;
	private final ChargeCodeMapper chargeCodeMapper;

	@Transactional
	public List<Integer> saveChargeCode(ChargeTypeEnterpriseDTO chargeTypeEnterpriseDTO) {
		ChargeType chargeType = chargeCodeRepository
				.save(chargeCodeMapper.fromChargeEnterpriseDTO(chargeTypeEnterpriseDTO));
		return chargeType.getChargeTypeBusinessUnitServiceOfferingAssociations().stream().map(
				ChargeTypeBusinessUnitServiceOfferingAssociation::getChargeTypeBusinessUnitServiceOfferingAssociationID)
				.collect(Collectors.toList());
	}

}
