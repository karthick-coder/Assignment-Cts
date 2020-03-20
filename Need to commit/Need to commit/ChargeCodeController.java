package com.jbhunt.pricing.agreement.controller;

import static com.jbhunt.pricing.agreement.constants.ChargeCodeConstants.SAVE_CHARGE_CODE;

import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jbhunt.pricing.agreement.dto.ChargeTypeEnterpriseDTO;
import com.jbhunt.pricing.agreement.service.ChargeCodeService;

import lombok.AllArgsConstructor;

@RestController
@AllArgsConstructor
public class ChargeCodeController {

	private final ChargeCodeService chargeCodeService;

	@PostMapping(value=SAVE_CHARGE_CODE)
	public ResponseEntity<List<Integer>> saveChargeCode(@RequestBody ChargeTypeEnterpriseDTO chargeTypeEnterpriseDTO) {

		return Optional.ofNullable(chargeCodeService.saveChargeCode(chargeTypeEnterpriseDTO))
				.map(response -> new ResponseEntity<List<Integer>>(response,HttpStatus.OK))
				.orElse(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
	}

}
